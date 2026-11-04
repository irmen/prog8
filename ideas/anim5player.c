/*
 * IFF ANIM5 player using SDL2 + libiff, structured the way an
 * on-system Amiga player works:
 *
 *   - two chip-RAM screens, page-flipped at each frame (double buffer)
 *   - op5 deltas decoded by the CPU, unvscomp-style: byte post-increment
 *     on the op stream, a dest pointer stepping by rowBytes, count loops
 *     that map to DBRA, and a precomputed skip table so no multiply is
 *     needed in the hot loop -- tuned for a 68020 (fast 32-bit ALU and
 *     barrel shifter, small 256-byte I-cache, no data cache);
 *   - the blitter does the bulk rectangular copies (bootstrap mirror,
 *     interleave=1 pre-copy, hold-frame copy, BODY plane fill-in): on
 *     the Amiga these run concurrently with the CPU, and a real port
 *     issues the blit and only waits for blit-done right before the
 *     plane is decoded into. Here they are synchronous stand-ins.
 *   - frame pacing is the vblank timer: each frame's delay is its ANHD
 *     reltime in jiffies, accounted against a target clock.
 *
 * Build: gcc -O2 -o anim5player anim5player.c $(pkg-config --cflags --libs libiff sdl2)
 * Usage: ./anim5player file.anim
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <SDL2/SDL.h>
#include <libiff/iff.h>
#include <libiff/form.h>
#include <libiff/rawchunk.h>
#include <libiff/defaultregistry.h>

#define IFF_MAKEID(a,b,c,d) ((unsigned int)(a)<<24 | (unsigned int)(b)<<16 | (unsigned int)(c)<<8 | (unsigned int)(d))

static uint32_t read_be32(const unsigned char *p) {
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) |
           ((uint32_t)p[2] << 8)  | (uint32_t)p[3];
}
static uint16_t read_be16(const unsigned char *p) {
    return ((uint16_t)p[0] << 8) | (uint16_t)p[1];
}

typedef struct {
    uint16_t width, height;
    uint8_t  nPlanes, compression;
} BMHD;

typedef struct {
    BMHD     bmhd;
    uint8_t  palette[256][3];
    uint16_t width, height, depth;
    uint16_t bytesPerRow;      /* words-rounded row width */
    uint32_t planeSize;
    int32_t  rowStep[256];     /* n -> n * bytesPerRow: skips and run
                                  advances need no multiply in the hot loop */
    uint16_t numColors;        /* 256-entry CMAPs exist (AGA 8-plane) */
    uint8_t **planes;
    SDL_Window   *window;
    SDL_Renderer *renderer;
    SDL_Texture  *texture;
} AnimState;

/* Bulk rectangular copies are blitter jobs on the Amiga: the CPU
   programs BLTCON0/BLTxPTH/BLTSIZE and the copy runs concurrently
   with CPU work; a real port issues the blit and waits for blit-done
   only right before the plane is decoded into. These are synchronous
   stand-ins preserving that call structure. */
static void blit_copy(uint8_t *dst, const uint8_t *src, uint32_t bytes) {
    memcpy(dst, src, bytes);
}

/* Per-plane fill-in from an interleaved BODY: on the Amiga one blit
   per plane, A-modulo (depth-1)*rowBytes skips the other planes' rows. */
static void blit_plane_from_body(uint8_t *dst, const uint8_t *src,
                                 uint16_t rowBytes, uint16_t rows,
                                 uint16_t srcModulo) {
    for (uint16_t y = 0; y < rows; y++) {
        memcpy(dst, src, rowBytes);
        dst += rowBytes;
        src += (uint32_t)rowBytes + srcModulo;
    }
}

/* Classic ByteRun1 (PackBits) expansion, 68020-friendly: byte stream
   in, byte stream out, count loops map to DBRA. */
static uint32_t byterun1_decode(const uint8_t *src, uint32_t srcSize,
                                uint8_t *dst, uint32_t dstSize) {
    uint32_t si = 0, di = 0;
    while (si < srcSize && di < dstSize) {
        int8_t n = (int8_t)src[si++];
        if (n >= 0) {
            uint16_t count = (uint16_t)n + 1;
            if (di + count > dstSize) count = (uint16_t)(dstSize - di);
            if (si + count > srcSize) count = (uint16_t)(srcSize - si);
            memcpy(dst + di, src + si, count);
            si += count; di += count;
        } else if (n != -128) {
            uint16_t count = (uint16_t)(-n) + 1;
            uint8_t val = src[si++];
            if (di + count > dstSize) count = (uint16_t)(dstSize - di);
            for (uint16_t i = 0; i < count; i++) dst[di++] = val;
        }
    }
    return di;
}

static void decode_ilbm_body(AnimState *anim, const uint8_t *body, uint32_t bodySize) {
    uint16_t h = anim->height, d = anim->depth, bpr = anim->bytesPerRow;
    const uint8_t *src = body;
    uint8_t *decomp = NULL;
    if (anim->bmhd.compression == 1) {
        /* ByteRun1 BODY: expand to a scratch buffer, then fill the
           planes from it the same way as an uncompressed BODY */
        decomp = malloc(anim->planeSize * d);
        byterun1_decode(body, bodySize, decomp, anim->planeSize * d);
        src = decomp;
    } else if (anim->bmhd.compression != 0) {
        return; /* unknown compression */
    }
    /* one blit per plane, A-modulo skips the other planes' rows */
    for (uint16_t p = 0; p < d; p++)
        blit_plane_from_body(anim->planes[p], src + (uint32_t)p * bpr,
                             bpr, h, (uint16_t)((d - 1) * bpr));
    free(decomp);
}

/* ANIM5 Byte Vertical Delta, per AmigaOS ANIM spec section 3.2.3
 * (and the AnimBrush DLTA note): the DLTA opens with SIXTEEN u32-BE
 * plane offsets (libiff chunkData-relative); only the first 8 are plane
 * data pointers, the second 8 are unused (zero). A zero pointer means
 * the plane is unchanged. First used pointer is therefore 64, not 32.
 * Each plane's data is a per-column stream over rowBytes columns; each
 * column starts with an op-COUNT byte, followed by exactly that many
 * ops:
 *   op==0         : Same run - next byte cnt, next byte value v;
 *                   write v to next cnt rows (STORE semantics, bits=0;
 *                   AnimBrush XOR variant with bits=4 not handled here).
 *   op hi bit clr : Skip op rows (non-zero).
 *   op hi bit set : Uniq run - low 7 bits = cnt; copy cnt literal bytes.
 * All write-ops advance the row cursor by cnt. Rows are addressed
 * vertically: byte (row,col) of the plane. Rosetta: oxideav-iff's
 * apply_op5 uses a 0-terminator framing instead of op-counts and misses
 * the Same op, so it fails on files like 2d_watch.anim. */
static void decode_anim5_delta(AnimState *anim, uint8_t **dstPlanes,
                                uint8_t **srcPlanes,
                                const uint8_t *dlta, uint32_t dltaSize) {
    uint16_t nPlanes = anim->depth;
    uint16_t rowBytes = anim->bytesPerRow;
    uint16_t height = anim->height;
    if (dltaSize < 64) return;
    /* interleave=1 reference copy: blitter job (async on the Amiga);
       skipped for interleave=2 where the delta applies in place */
    for (uint16_t p = 0; p < nPlanes; p++) {
        if (dstPlanes[p] == srcPlanes[p]) continue;
        blit_copy(dstPlanes[p], srcPlanes[p], anim->planeSize);
    }
    for (uint16_t p = 0; p < nPlanes && p < 8; p++) {
        uint32_t off = read_be32(dlta + p * 4);
        if (off == 0) continue;             /* plane unchanged */
        if (off >= dltaSize) continue;      /* corrupt pointer */
        const uint8_t *s = dlta + off;      /* A-register, (s)+ post-inc */
        const uint8_t *send = dlta + dltaSize;
        uint8_t *plane = dstPlanes[p];
        for (uint16_t col = 0; col < rowBytes; col++) {
            if (s >= send) return; /* truncated */
            uint16_t nops = *s++;
            /* dp is the logical row cursor, walking rowBytes per row;
               left tracks rows remaining (can go negative via skip) */
            uint8_t *dp = plane + col;
            int32_t left = height;
            for (uint16_t k = 0; k < nops; k++) {
                if (s >= send) return;
                uint8_t op = *s++;
                if (op == 0) {
                    if (s + 1 >= send) return;
                    int32_t cnt = *s++;
                    uint8_t v = *s++;
                    int32_t run = cnt < left ? cnt : left; /* <=0 clips */
                    /* DBRA loop: same-byte fill down the column */
                    uint8_t *wp = dp;
                    for (int32_t r = 0; r < run; r++) {
                        *wp = v;
                        wp += rowBytes;
                    }
                    dp += anim->rowStep[cnt]; /* full logical advance */
                    left -= cnt;
                } else if ((op & 0x80) == 0) {
                    /* skip: table lookup, no multiply */
                    dp += anim->rowStep[op];
                    left -= op;
                } else {
                    int32_t cnt = op & 0x7F;
                    if (s + cnt > send) return;
                    int32_t run = cnt < left ? cnt : left;
                    /* DBRA loop: literal bytes down the column */
                    uint8_t *wp = dp;
                    for (int32_t r = 0; r < run; r++) {
                        *wp = *s++;
                        wp += rowBytes;
                    }
                    s += cnt - run; /* clipped tail is consumed, not written */
                    dp += anim->rowStep[cnt];
                    left -= cnt;
                }
            }
        }
    }
}

static uint32_t palARGB[256]; /* palette pre-baked to ARGB8888 */

static void bake_palette(AnimState *anim) {
    for (int i = 0; i < 256; i++)
        palARGB[i] = 0xFF000000u | ((uint32_t)anim->palette[i][0] << 16) |
                     ((uint32_t)anim->palette[i][1] << 8) | anim->palette[i][2];
}

/* Host display glue: on the Amiga there is no such step -- the copper
   displays the visible screen's bitplanes directly from chip RAM, at
   zero CPU cost. This plain planar-to-RGBA expansion exists only so
   the SDL window shows the same picture; it is not part of the Amiga
   playback model and deliberately stays simple. */
static void render_frame(AnimState *anim) {
    uint16_t h = anim->height, d = anim->depth, bpr = anim->bytesPerRow;
    void *pixels; int pitch;
    SDL_LockTexture(anim->texture, NULL, &pixels, &pitch);
    uint32_t *px = (uint32_t *)pixels;
    uint32_t rowWords = (uint32_t)pitch / 4;
    for (uint16_t y = 0; y < h; y++) {
        uint32_t *dst = px + (uint32_t)y * rowWords;
        uint32_t rowBase = (uint32_t)y * bpr;
        for (uint16_t x = 0; x < anim->width; x++) {
            uint32_t byteIdx = rowBase + (x >> 3);
            uint8_t bit = (uint8_t)(7 - (x & 7));
            uint16_t ci = 0;
            for (uint16_t p = 0; p < d; p++)
                if (anim->planes[p][byteIdx] & (1 << bit)) ci |= (uint16_t)(1 << p);
            dst[x] = palARGB[ci];
        }
    }
    SDL_UnlockTexture(anim->texture);
    SDL_RenderClear(anim->renderer);
    SDL_RenderCopy(anim->renderer, anim->texture, NULL, NULL);
    SDL_RenderPresent(anim->renderer);
}

/* Helper: find a chunk in a form by ID */
static IFF_RawChunk *find_chunk(IFF_Form *form, unsigned int id) {
    for (unsigned int i = 0; i < form->chunkLength; i++) {
        IFF_RawChunk *rc = (IFF_RawChunk *)form->chunk[i];
        if (rc->chunkId == id) return rc;
    }
    return NULL;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "Usage: %s <file.anim>\n", argv[0]); return 1; }

    IFF_Chunk *chunk = IFF_readFile(argv[1], &IFF_defaultChunkRegistry);
    if (!chunk) { fprintf(stderr, "Failed to read IFF file\n"); return 1; }

    IFF_Form *form = (IFF_Form *)chunk;
    if (form->formType != IFF_MAKEID('A','N','I','M')) {
        fprintf(stderr, "Not an ANIM file\n");
        IFF_freeChunk(chunk, 0, &IFF_defaultChunkRegistry);
        return 1;
    }

    AnimState anim;
    memset(&anim, 0, sizeof(anim));

    /* First FORM ILBM = first frame with BMHD, CMAP, BODY */
    IFF_Form *firstFrame = (IFF_Form *)form->chunk[0];
    /* Extract BMHD */
    IFF_RawChunk *bmhdChunk = find_chunk(firstFrame, IFF_MAKEID('B','M','H','D'));
    if (bmhdChunk) {
        const unsigned char *p = bmhdChunk->chunkData;
        anim.bmhd.width = read_be16(p);
        anim.bmhd.height = read_be16(p+2);
        anim.bmhd.nPlanes = p[8];
        anim.bmhd.compression = p[10];
        anim.width = anim.bmhd.width;
        anim.height = anim.bmhd.height;
        anim.depth = anim.bmhd.nPlanes;
        anim.bytesPerRow = ((anim.width + 15) / 16) * 2;
        anim.planeSize = (uint32_t)anim.bytesPerRow * anim.height;
        /* row-advance table: skips and run ends index instead of
           multiplying -- classic 68k table trade of RAM for cycles */
        for (uint16_t i = 0; i < 256; i++)
            anim.rowStep[i] = (int32_t)i * anim.bytesPerRow;
    }

    /* Extract CMAP */
    IFF_RawChunk *cmapChunk = find_chunk(firstFrame, IFF_MAKEID('C','M','A','P'));
    if (cmapChunk) {
        anim.numColors = cmapChunk->chunkSize / 3;
        for (int j = 0; j < anim.numColors && j < 256; j++) {
            anim.palette[j][0] = cmapChunk->chunkData[j*3];
            anim.palette[j][1] = cmapChunk->chunkData[j*3+1];
            anim.palette[j][2] = cmapChunk->chunkData[j*3+2];
        }
    }

    if (anim.width == 0 || anim.height == 0) {
        fprintf(stderr, "No BMHD found\n");
        return 1;
    }
    bake_palette(&anim);

    /* SDL init */
    SDL_Init(SDL_INIT_VIDEO);
    anim.window = SDL_CreateWindow("ANIM5 Player", SDL_WINDOWPOS_CENTERED,
                                  SDL_WINDOWPOS_CENTERED, anim.width, anim.height, 0);
    anim.renderer = SDL_CreateRenderer(anim.window, -1, SDL_RENDERER_ACCELERATED);
    anim.texture = SDL_CreateTexture(anim.renderer, SDL_PIXELFORMAT_ARGB8888,
                                    SDL_TEXTUREACCESS_STREAMING, anim.width, anim.height);

    int nFrames = form->chunkLength;

    /* Two screens, double-buffered, exactly like an on-system Amiga
       player (ANIM spec "Playing ANIMs"): the BODY seed goes into the
       hidden screen, is flipped into view, and is copied to the other
       screen so both start as frame 0 (§1.3 bootstrap). Each delta
       then modifies the hidden screen n frames back (ANHD interleave,
       0 means 2; only 1 and 2 exist in practice) and the screens flip.
       n = 2 applies in place to its own slot (op5 ops only overwrite,
       never re-read); n = 1 copies the shown screen over first, which
       decode_anim5_delta already does for differing src/dst.
       Looping follows the spec recipe instead of re-reading the BODY:
       a looping file ends with two frames identical to the first two,
       so play resumes from file frame L = interleave of the last frame
       (D2 double-buffered, D1 AnimBrush), whose reference is exactly
       what the hidden screen holds. No frame is ever cached; only tiny
       per-frame delay/interleave tables. */
    uint32_t *delays = calloc(nFrames, sizeof(uint32_t));
    int *ils = calloc(nFrames, sizeof(int));
    delays[0] = 100; /* show seed frame briefly before deltas roll */
    for (int f = 1; f < nFrames; f++) {
        IFF_Form *frame = (IFF_Form *)form->chunk[f];
        IFF_RawChunk *anhd = find_chunk(frame, IFF_MAKEID('A','N','H','D'));
        uint32_t reltime = 1;
        int il = 2; /* 0 means 2 (double-buffer default) */
        if (anhd && anhd->chunkSize >= 24) {
            reltime = read_be32(anhd->chunkData + 14);
            il = anhd->chunkData[18];
            if (il == 0) il = 2;
            if (il < 1 || il > 2) {
                fprintf(stderr, "frame %d: unsupported interleave %d "
                        "(only 1 and 2 exist in practice)\n", f, il);
                return 1;
            }
        }
        /* reltime is in jiffies (1/60 s); ANHD reltime 0 = use 2 */
        if (reltime == 0) reltime = 2;
        delays[f] = reltime * 1000 / 60;
        if (delays[f] < 16) delays[f] = 16;
        ils[f] = il;
    }
    /* bufs[s][p] = live planes; anim.planes aliases the slot shown */
    unsigned char ***bufs = calloc(2, sizeof(unsigned char **));
    for (int s = 0; s < 2; s++) {
        bufs[s] = calloc(anim.depth, sizeof(unsigned char *));
        for (int p = 0; p < anim.depth; p++)
            bufs[s][p] = calloc(anim.planeSize, 1);
    }
    fprintf(stderr, "%d frames (%dx%dx%d), 2 screens, double-buffered\n",
            nFrames, anim.width, anim.height, anim.depth);

    /* Seed into screen 0, mirror to screen 1 (blitter copy), flip up */
    anim.planes = bufs[0];
    {
        IFF_RawChunk *body = find_chunk(firstFrame, IFF_MAKEID('B','O','D','Y'));
        if (body)
            decode_ilbm_body(&anim, body->chunkData, body->chunkSize);
        for (uint16_t p = 0; p < anim.depth; p++)
            blit_copy(bufs[1][p], bufs[0][p], anim.planeSize);
    }
    render_frame(&anim);

    /* Pre-resolve per-frame chunk pointers and the loop-resume point so
       playback never re-searches the IFF tree; mirrors what an Amiga
       player would know up front after one pass over the file. */
    long M = nFrames - 1;
    int L = (M >= 1) ? ils[M] : 1; /* loop resumes here (spec recipe) */
    int loopLen = (M >= 2) ? (int)(M - L + 1) : 0;
    IFF_RawChunk **deltas = calloc(nFrames, sizeof(IFF_RawChunk *));
    for (int f = 1; f < nFrames; f++) {
        IFF_Form *frame = (IFF_Form *)form->chunk[f];
        IFF_RawChunk *dlta = find_chunk(frame, IFF_MAKEID('D','L','T','A'));
        if (!dlta) dlta = find_chunk(frame, IFF_MAKEID('B','O','D','Y'));
        deltas[f] = dlta;
    }

    /* Playback: file frame f walks D1..DM then cycles D(L)..DM; the
       screen slot toggles per frame (ref = slot for n=2 in place, or
       the other slot for n=1 -- identical to the v%2 parity math, but
       without tracking v). The vblank-timer equivalent is a target
       clock: decode/render time is compensated by delaying to the due
       instant, and falling behind resyncs instead of bursting. */
    int quit = 0;
    int f = (M >= 1) ? 1 : -1;
    int slot = 1; /* first delta lands on screen 1 (seed shown on 0) */
    uint64_t nextDue = SDL_GetTicks64() + delays[0];
    while (!quit) {
        SDL_Event e;
        while (SDL_PollEvent(&e)) {
            if (e.type == SDL_QUIT) quit = 1;
            if (e.type == SDL_KEYDOWN && e.key.keysym.sym == SDLK_ESCAPE) quit = 1;
        }
        if (quit) break;

        if (f >= 1) {
            IFF_RawChunk *dlta = deltas[f];
            if (dlta) {
                int n = ils[f];
                int ref = (n == 2) ? slot : (slot ^ 1);
                decode_anim5_delta(&anim, bufs[slot], bufs[ref],
                                   dlta->chunkData, dlta->chunkSize);
            } else {
                /* no delta payload: hold previous frame (blitter copy) */
                unsigned char **prev = bufs[slot ^ 1];
                for (uint16_t p = 0; p < anim.depth; p++)
                    blit_copy(bufs[slot][p], prev[p], anim.planeSize);
            }
            anim.planes = bufs[slot];
            render_frame(&anim);

            uint64_t now = SDL_GetTicks64();
            nextDue += delays[f];
            if (now < nextDue)
                SDL_Delay((uint32_t)(nextDue - now));
            else
                nextDue = now; /* fell behind: resync, don't burst */

            slot ^= 1;
            f++;
            if (f > M)
                f = (loopLen > 0) ? L : -1;
        } else {
            SDL_Delay(delays[M >= 0 ? M : 0]);
        }
    }

    free(deltas);
    for (int s = 0; s < 2; s++) {
        for (int p = 0; p < anim.depth; p++) free(bufs[s][p]);
        free(bufs[s]);
    }
    free(bufs);
    free(delays);
    free(ils);
    SDL_DestroyTexture(anim.texture);
    SDL_DestroyRenderer(anim.renderer);
    SDL_DestroyWindow(anim.window);
    SDL_Quit();
    IFF_freeChunk(chunk, 0, &IFF_defaultChunkRegistry);
    return 0;
}
