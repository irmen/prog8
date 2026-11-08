; "unlimited sprites / bobs" demo effect, for the C64 with a Vera64X board.
; Port of examples/cx16/bobs.p8.
; Note that everything is prog8, no inline assembly used/required.
; Note2: these aren't actual sprites, it's a bitmap backbuffer display trick.
;
; The main loop waits for VERA's vertical blank and flips the display buffer there.

%import conv
%import math
%import textio
%import vera
%import vera_palette

%option no_sysinit

main {
    sub start() {
        txt.print("\n\n infinite sprites on the vera64x\n")

        vera.reset()

        ; enable bitmap mode 320x200, 1 bpp, only layer 1, VGA output
        vera.VERA_CTRL = 0
        vera.VERA_DC_VIDEO = (vera.VERA_DC_VIDEO & %11001111) | %00100001
        vera.VERA_DC_HSCALE = 64
        vera.VERA_DC_VSCALE = 64
        vera.VERA_L1_CONFIG = %00000100
        vera.VERA_L1_MAPBASE = 0
        vera.VERA_L1_TILEBASE = 0

        ; limit display height to 200 pixels to have enough vram for 12 backbuffers
        const ubyte vstart = 20
        const ubyte vheight = 200
        vera.VERA_CTRL = %00000010
        vera.VERA_DC_VSTART = vstart
        vera.VERA_DC_VSTOP = vstart + vheight - 1

        init_buffers()
        vera_palette.set_color(0, $000)
        vera_palette.set_color(1, $af8)
        precalc_shifts()

        repeat {
            ; draw 4 bobs per frame to speed up bob count
            repeat 4 {
                ubyte vmembase = blitbuffer*4               ; 2048 * 4 per backbuffer
                blit(vmembase)
                blitbuffer++
                if blitbuffer==num_backbuffers {
                    blitbuffer=0
                }
            }

            backbuffer++
            if backbuffer==num_backbuffers {
                backbuffer=0
            }
            ; increment bob counter every 3 frames:
            ; with 12 backbuffers and 4 bobs drawn per frame,
            ; each backbuffer only receives a new bob once every 3 frames (12/4=3).
            ; so the visible bob count increases by 1 every 3 frames.
            frame_count++
            if frame_count >= 3 {
                frame_count = 0
                num_bobs++
            }

            vmembase = backbuffer*4                     ; 2048 * 4 per backbuffer
            draw_number(vmembase, num_bobs)

            vera.waitvsync()
            vera.VERA_L1_TILEBASE = vmembase << 2       ; flip to next backbuffer
        }
    }

    const ubyte num_backbuffers = 12        ; there is vram space for 14 backbuffers.  reduce to make tighter "loops"
    const ubyte sprite_size = 16*3          ; bytes per (shifted) bob, 3 columns of 16 pixels
    uword num_bobs = 0
    ubyte backbuffer = num_backbuffers-1
    ubyte blitbuffer = 0
    ubyte frame_count = 0
    uword anim1 = $0432
    uword anim2 = $f123
    uword anim3 = $e321
    uword anim4 = $7500

    sub init_buffers() {
        ; erase all vram (128 Kb)
        verafx.clear(0, 0, 0, 32768)
    }

    sub precalc_shifts() {
        ; shifting the sprite bitmaps on the fly is far too slow on the C64 (it costs more
        ; than the actual blit), so precalculate all 8 pixel shift variants just once
        ubyte shift
        for shift in 0 to 7 {
            bitshift(shift)
        }
    }

    sub blit(ubyte vmembase) {
        ubyte bank = vmembase>=32 as ubyte
        uword vmem = vmembase * 2048        ; mkword(vmembase,0) * 8
        uword blit_x = (math.cos8u(msb(anim1)) as uword) + math.sin8u(msb(anim2))/6
        ubyte blit_y = math.sin8u(msb(anim3))/2  + math.cos8u(msb(anim4))/5
        vmem += blit_x/8 + (blit_y as uword) * 40

        ubyte shift = lsb(blit_x) & 7
        ^^ubyte spr = &shifted_sprites_a
        ^^ubyte msk = &shifted_masks_a
        if shift >= 4 {
            spr = &shifted_sprites_b
            msk = &shifted_masks_b
            shift -= 4
        }
        spr += shift*sprite_size
        msk += shift*sprite_size

        ; left column of the (shifted) "sprite" (bob)
        vera.VERA_CTRL = 0
        vera.VERA_ADDR_L = lsb(vmem)
        vera.VERA_ADDR_M = msb(vmem)
        vera.VERA_ADDR_H = bank | %10110000     ; increment 40 for read (next line)
        vera.VERA_CTRL = 1
        vera.VERA_ADDR_L = lsb(vmem)
        vera.VERA_ADDR_M = msb(vmem)
        vera.VERA_ADDR_H = bank | %10110000     ; increment 40 for read (next line)
        ubyte ix
        for ix in 0 to sprite_size-1 step 3
            vera.VERA_DATA1 = vera.VERA_DATA0 & msk[ix] | spr[ix]

        ; middle column of the (shifted) "sprite" (bob)
        vmem++
        vera.VERA_CTRL = 0
        vera.VERA_ADDR_L = lsb(vmem)
        vera.VERA_ADDR_M = msb(vmem)
        vera.VERA_CTRL = 1
        vera.VERA_ADDR_L = lsb(vmem)
        vera.VERA_ADDR_M = msb(vmem)
        for ix in 1 to sprite_size-1 step 3
            vera.VERA_DATA1 = vera.VERA_DATA0 & msk[ix] | spr[ix]

        ; right column of the (shifted) "sprite" (bob)
        vmem++
        vera.VERA_CTRL = 0
        vera.VERA_ADDR_L = lsb(vmem)
        vera.VERA_ADDR_M = msb(vmem)
        vera.VERA_CTRL = 1
        vera.VERA_ADDR_L = lsb(vmem)
        vera.VERA_ADDR_M = msb(vmem)
        for ix in 2 to sprite_size-1 step 3
            vera.VERA_DATA1 = vera.VERA_DATA0 & msk[ix] | spr[ix]

        anim1 += 117
        anim2 += 90
        anim3 += 122
        anim4 += 145
    }

    sub bitshift(ubyte shift) {
        ^^ubyte sprdst = &shifted_sprites_a
        ^^ubyte mskdst = &shifted_masks_a
        ubyte offset = shift
        if shift >= 4 {
            sprdst = &shifted_sprites_b
            mskdst = &shifted_masks_b
            offset = shift - 4
        }
        ubyte yix = offset*sprite_size
        ubyte yy
        for yy in 0 to 15 {
            uword @zp sprw = sprite[yy]
            uword @zp maskw = mask[yy]
            ubyte @zp sprite_3 = 0
            ubyte @zp mask_3 = 255
            repeat shift {
                sprw >>= 1
                ror(sprite_3)
                sys.set_carry()
                ror(maskw)
                ror(mask_3)
            }
            sprdst[yix] = msb(sprw)
            mskdst[yix] = msb(maskw)
            yix++
            sprdst[yix] = lsb(sprw)
            mskdst[yix] = lsb(maskw)
            yix++
            sprdst[yix] = sprite_3
            mskdst[yix] = mask_3
            yix++
        }
    }

    sub draw_number(ubyte vmembase, uword number) {
        uword vmem = vmembase * 2048        ; mkword(vmembase,0) * 8
        ubyte bank = vmembase>=32 as ubyte
        vmem += 35
        ^^ubyte number_str = conv.str_uw0(number)
        uword pixelsptr = &numberpixels + (number_str[1] & 15)*7
        ubyte pix
        vera.VERA_CTRL = 0
        vera.VERA_ADDR_L = lsb(vmem)
        vera.VERA_ADDR_M = msb(vmem)
        vera.VERA_ADDR_H = bank | %10110000       ; increment 40 for read (next line)
        for pix in 0 to 6
            vera.VERA_DATA0 = pixelsptr[pix]
        vmem++
        vera.VERA_ADDR_L = lsb(vmem)
        vera.VERA_ADDR_M = msb(vmem)
        pixelsptr = &numberpixels + (number_str[2] & 15)*7
        for pix in 0 to 6
            vera.VERA_DATA0 = pixelsptr[pix]
        vmem++
        vera.VERA_ADDR_L = lsb(vmem)
        vera.VERA_ADDR_M = msb(vmem)
        pixelsptr = &numberpixels + (number_str[3] & 15)*7
        for pix in 0 to 6
            vera.VERA_DATA0 = pixelsptr[pix]
        vmem++
        vera.VERA_ADDR_L = lsb(vmem)
        vera.VERA_ADDR_M = msb(vmem)
        pixelsptr = &numberpixels + (number_str[4] & 15)*7
        for pix in 0 to 6
            vera.VERA_DATA0 = pixelsptr[pix]
    }

    ubyte[10*7] numberpixels = [
        %00111000,
        %01000100,
        %10000100,
        %10000100,
        %10000100,
        %01111000,
        %00000000,

        %00010000,
        %00110000,
        %01010000,
        %00010000,
        %00010000,
        %01111100,
        %00000000,

        %01111000,
        %10000100,
        %00011000,
        %00110000,
        %01100000,
        %11111100,
        %00000000,

        %01111000,
        %00000100,
        %00111000,
        %00000100,
        %00000100,
        %11111000,
        %00000000,

        %00010100,
        %00100100,
        %01000100,
        %11111100,
        %00000100,
        %00000100,
        %00000000,

        %11111000,
        %10000000,
        %11111000,
        %00000100,
        %00000100,
        %11111000,
        %00000000,

        %01111000,
        %10000000,
        %11111000,
        %10000100,
        %10000100,
        %01111000,
        %00000000,

        %11111100,
        %00001000,
        %00010000,
        %00010000,
        %00010000,
        %00010000,
        %00000000,

        %01111000,
        %10000100,
        %01111000,
        %10000100,
        %10000100,
        %01111000,
        %00000000,

        %01111000,
        %10000100,
        %01111100,
        %00000100,
        %10000100,
        %01111000,
        %00000000
    ]

    uword[16] sprite = [
        %0000001111000000,
        %0001111111111000,
        %0011111110101100,
        %0111111011010010,
        %0111111010100010,
        %0111110100001010,
        %1111101000100001,
        %1111010000000001,
        %1111000100100001,
        %1110100000000001,
        %0110001000000010,
        %0101000000000010,
        %0100000000000010,
        %0010100000000100,
        %0001110000111000,
        %0000001111000000
    ]

    uword[16] mask = [
        %1111110000111111,
        %1110000000000111,
        %1100000000000011,
        %1000000000000001,
        %1000000000000001,
        %1000000000000001,
        %0000000000000000,
        %0000000000000000,
        %0000000000000000,
        %0000000000000000,
        %1000000000000001,
        %1000000000000001,
        %1000000000000001,
        %1100000000000011,
        %1110000000000111,
        %1111110000111111
    ]

    ; the precalculated shifted sprite bitmaps (shifts 0-3 and 4-7, byte arrays are max 256 long)
    ubyte[4*sprite_size] shifted_sprites_a
    ubyte[4*sprite_size] shifted_masks_a
    ubyte[4*sprite_size] shifted_sprites_b
    ubyte[4*sprite_size] shifted_masks_b
}
