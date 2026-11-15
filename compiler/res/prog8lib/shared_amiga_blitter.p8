%import custom
%import mobs

blitter {
    %option merge, no_symbol_prefixing, ignore_unused

    private uword @shared line_pattern
    private uword @shared line_mod

    ; Bare-metal blitter helpers for rectangle copies, fills, masked blits and
    ; line drawing with the MINTERM logic modes below. These routines make no OS
    ; calls and are intended for use after custom.grab_system(). The CPU and
    ; blitter run concurrently; see WAITING BEHAVIOR below.
    ;
    ; SCREEN LAYOUT: Intended for non-interleaved planar bitmaps with separate,
    ; contiguous bitplanes. Rectangle and BOB routines blit one plane per call,
    ; allowing planes to be allocated independently and letting BOB planes share
    ; one mask. The tradeoff is one blit setup per plane. Line-interleaved screens
    ; can reduce setups with taller blits, but require interleaved source data and
    ; repeated masks, and reduce the maximum blit height by the plane count.

    ; WARNING: Pointers used by blitter DMA must point to CHIP RAM. The hardware
    ; cannot access fast RAM directly; using a fast-RAM DMA pointer can cause
    ; bus errors or corrupted data. CPU-read descriptors and plane tables need
    ; not be in chip RAM, but the image and destination data they reference must.
    ;
    ; WIDTH LIMITATION: A blit can be at most 64 words (1024 pixels) wide.
    ; Standard Amiga screens (320/640 pixels) fit within this limit. Split wider
    ; images into multiple blits.
    ;
    ; WAITING BEHAVIOR: Operations wait for the blitter to become idle before
    ; programming its registers or accessing target chip memory. CPU-only setup
    ; can overlap a previous blit. Calls return after starting a blit, not after
    ; it completes. Call blitter.wait() before reading modified memory or
    ; swapping display buffers.

    const uword MINTERM_MASKED = $CA    ; (A AND B) OR (NOT A AND C) - masked blit
    const uword MINTERM_A_ONLY = $F0    ; channel A only (same as COPY)
    const uword MINTERM_NOT_A = $0F     ; NOT A - invert source
    const uword MINTERM_B_ONLY = $CC    ; channel B only
    const uword MINTERM_A_XOR_C = $5A   ; A XOR C (erase/restore patterns)
    const uword MINTERM_MASKED_OR = $EA ; C OR (A AND B) - additive overlay with mask
    const uword MINTERM_OR = $FA        ; A OR C - simple overlay, no mask
    const uword MINTERM_ERASE = $0A     ; (NOT A) AND C - erase where source is set
    const uword MINTERM_IDENTITY = $AA  ; C - no-op (used with BLTCDAT for fills)
    const uword MINTERM_CLEAR = $00     ; 0 - clear destination (all zeros)
    const uword MINTERM_SET = $FF       ; set destination to all ones
    const uword MINTERM_AND = $3C       ; (A AND B) AND C - keep where all three set
    const uword MINTERM_COND = $AC      ; (A AND C) OR (B AND NOT C) - conditional replace

    asmsub wait() {
        ; Wait until the blitter is idle.
        ; Call before reading modified memory or swapping buffers.
        %asm {{
.busy:
            btst.b  #6,custom.DMACONR
            bne.s   .busy
            rts
        }}
    }

    asmsub set_blitpri(bool hasprio @D0) {
        ; Set or clear the blitter's priority over the CPU.
        ; Priority speeds up blits but slows the CPU while they run.
        %asm {{
            tst.b   d0
            beq.s   .clear
            move.w  #$8400,custom.DMACON   ; SET bit + BLITPRI bit
            rts
.clear:
            move.w  #$0400,custom.DMACON   ; BLITPRI bit only (clears it)
            rts
        }}
    }

    ; Copy a rectangle from src to dst.
    ; Only channels A and D are configured, so use an A-only minterm:
    ; MINTERM_A_ONLY (copy), MINTERM_NOT_A (invert), MINTERM_CLEAR or
    ; MINTERM_SET. Minterms that use B or C read unset channels.
    ; The blit width must be <= the destination screen width (modulo >= 0).
    ; Waits for the blitter to become idle, then returns after starting the blit.
    asmsub copy_rect(pointer src @A0, pointer dst @A1, uword width_words @D0, uword height @D1, uword src_mod @D2, uword dst_mod @D3, uword minterm @D4, bool descending @D5) {
        %asm {{
        movem.l d6-d7,-(sp)
        move.w  d0,d6           ; d6 = width
        move.w  d1,d7           ; d7 = height
        tst.b   d5
        beq.s   .no_desc
        move.w  d6,d0
        lsl.w   #1,d0
        add.w   d2,d0
        move.w  d7,d1
        subq.w  #1,d1
        mulu.w  d0,d1
        move.w  d6,d0
        subq.w  #1,d0
        lsl.w   #1,d0
        ext.l   d0
        add.l   d0,d1
        add.l   d1,a0
        move.w  d6,d0
        lsl.w   #1,d0
        add.w   d3,d0
        move.w  d7,d1
        subq.w  #1,d1
        mulu.w  d0,d1
        move.w  d6,d0
        subq.w  #1,d0
        lsl.w   #1,d0
        ext.l   d0
        add.l   d0,d1
        add.l   d1,a1
.no_desc:
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        move.w  d2,custom.BLTAMOD
        move.w  d3,custom.BLTDMOD
        move.w  #$ffff,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
        move.l  a0,custom.BLTAPT
        move.l  a1,custom.BLTDPT
        move.w  d4,d1
        or.w    #$0900,d1
        swap    d1
        move.w  #$0800,d1
        tst.b   d5
        beq.s   .nodesc
        ori.w   #$0002,d1
.nodesc:
        move.l  d1,custom.BLTCON0
        move.w  d7,d1
        lsl.w   #6,d1
        or.w    d6,d1
        move.w  d1,custom.BLTSIZE
        movem.l (sp)+,d6-d7
        rts
        }}
    }

    ; Fill a rectangle with a pattern.
    ; The blit width must be <= the destination screen width (modulo >= 0).
    ; Waits for the blitter to become idle, then returns after starting the blit.
    asmsub fill_rect(pointer dst @A0, uword width_words @D0, uword height @D1, uword pattern @D2, uword modulo @D3) {
        %asm {{
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        move.w  d3,custom.BLTDMOD
        move.w  d2,custom.BLTCDAT
        move.l  a0,custom.BLTDPT
        move.l  #(((custom.BC0F_DEST|blitter.MINTERM_IDENTITY)<<16)|$0800),custom.BLTCON0
        lsl.w   #6,d1
        or.w    d0,d1
        move.w  d1,custom.BLTSIZE
        rts
        }}
    }

    ; mask: first/last word mask (usually $ffff for full-word lines)
    ; pixel: line pixel pattern in BLTADAT (usually $8000 for one pixel)
    ; pattern: line fill pattern in BLTBDAT (usually $ffff for solid lines)
    ; screen_mod: destination bitplane row pitch in bytes
    ; Waits for the blitter to become idle, then returns after setup.
    asmsub line_init(uword mask @D0, uword pixel @D1, uword pattern @D2, uword screen_mod @D4) {
        %asm {{
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        move.w  d0,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
        move.w  d1,custom.BLTADAT
        move.w  d2,custom.BLTBDAT
        move.w  d4,custom.BLTCMOD
        move.w  d4,custom.BLTDMOD
        move.w  d2,blitter.line_pattern
        move.w  d4,blitter.line_mod
        rts
        }}
    }

    ; Draw a line in an exact pen colour across a multi-plane bitmap.
    ; For each bitplane, set the line's pixels if the matching pen bit is 1,
    ; or clear them if it is 0. This replaces the pixels along the line instead
    ; of OR-ing the pen with the existing colour. Call line_init() first with
    ; the bitmap's BytesPerRow.
    ; planes must point to an array of at least depth 32-bit plane addresses.
    ; For a graphics.BitMap, pass `&bitmap.Planes as ^^pointer`; bare-metal
    ; callers can pass their own plane table.
    sub line_color(uword x1, uword y1, uword x2, uword y2, ubyte pen, ubyte depth, ^^pointer planes) {
        for plane in 0 to depth-1 {
            bool isSet = ((pen >> plane) & 1) != 0
            if isSet
                line_draw(x1, y1, x2, y2, planes[plane], false, false)
            else
                line_draw(x1, y1, x2, y2, planes[plane], true, false)
        }
    }

    ; Draw a line using the blitter's hardware Bresenham mode.
    ; Call line_init() first with the appropriate parameters.
    ; Uses the octant-lookup-table algorithm from coppershade.org
    ; (see https://coppershade.org/asmskool/SOURCES/Developing-Demo-Effects/DDE4/LineVectors/).
    ; x1, y1: starting coordinates (absolute, any direction supported)
    ; x2, y2: ending coordinates
    ; screen_ptr: pointer to bitplane memory (CHIP RAM)
    ; erase: clear the line's pixels instead of setting them
    ; fill: draw a top-to-bottom XOR boundary with ONEDOT set. Horizontal edges
    ;   are omitted and must be drawn after area_fill(). If both flags are set,
    ;   fill takes precedence over erase.
    ; Waits for the blitter to become idle, then returns after starting the blit.
    asmsub line_draw(uword x1 @D0, uword y1 @D1, uword x2 @D2, uword y2 @D3, pointer screen_ptr @A1, bool erase @D5, bool fill @D7) {
        %asm {{
        tst.b   d7
        beq.s   .not_fill_line
        cmp.w   d1,d3
        beq.s   .empty_fill_line
        movem.l d2-d6/a2-a4,-(sp)
        bge.s   .fill_down
        exg     d0,d2
        exg     d1,d3
.fill_down:
        cmp.w   d1,d3
        beq     .finish
        bra.s   .line_setup
.not_fill_line:

        ; Axis-aligned fast path: dispatch horizontal spans to the CPU routine.
        cmp.w   d1,d3
        bne.s   .save_for_line
        ; horizontal: y1 == y2
        move.l  d3,-(sp)
        move.w  blitter.line_pattern,d3
        bsr     blitter.horiz_line
        move.l  (sp)+,d3
        rts
.save_for_line:
        movem.l d2-d6/a2-a4,-(sp)
        bra.s   .line_setup
.empty_fill_line:
        rts

.line_setup:
        ; Set up CPU-side values here for a self-contained call. LineInit
        ; normally sets these values once and preserves them between calls.
        ; minterm pack: $B0000000 | (minterm << 20) | $0f. After the
        ; and.w/ror.l #4/octant dance below this yields
        ; BLTCON0 = $xBRM (x = x1 & $f shift, B = SRCA|SRCC|SRCD, minterm)
        ; and BLTCON1 high byte $00; the octant table fills BLTCON1's low byte.
        move.l  #($B0000000)|(blitter.MINTERM_OR<<20)|$0000000f,a4    ; draw: D = A | C
        tst.b   d5
        beq.s   .not_erase
        move.l  #($B0000000)|(blitter.MINTERM_ERASE<<20)|$0000000f,a4 ; erase: D = ~A & C
.not_erase:
        tst.b   d7
        beq.s   .minterm_ready
        move.l  #($B0000000)|(blitter.MINTERM_A_XOR_C<<20)|$0000000f,a4 ; fill: D = A XOR C
.minterm_ready:

        tst.b   d7
        beq.s   .fill_start_ready
        movea.l a1,a2
        move.w  d1,d4
        mulu.w  blitter.line_mod,d4
        adda.l  d4,a2
        move.w  d0,d4
        lsr.w   #3,d4
        adda.w  d4,a2
        move.w  d0,d4
        and.w   #$000f,d4
        eor.w   #$000f,d4
        move.w  d4,a3
.fill_start_ready:

        ; d0=x1, d1=y1, d2=x2, d3=y2, d4=screen_mod, a1=screen_ptr
        move.l  a4,d6                ; minterm+mask
        and.w   d0,d6                ; mask x 0..15
        ror.l   #4,d6                ; shift it in at top, low word cleared

        ; --- deltas ---
        sub.w   d1,d3                ; d3 = y2 - y1
        bpl.s   .dyplus
        neg.w   d3
        addq.b  #8,d6
.dyplus:
        sub.w   d0,d2                ; d2 = x2 - x1
        bgt.s   .dxplus
        neg.w   d2
        addq.b  #4,d6
.dxplus:
        cmp.w   d2,d3
        bge.s   .dylarger
        exg     d2,d3                ; d2=Small delta, d3=Large delta
        addq.b  #2,d6
.dylarger:

        ; --- blit values ---
        move.w  blitter.line_mod,d4
        muls    d4,d1                ; d1 = y1 * screen_mod
        asr.w   #3,d0                ; bit 0 ignored by Blitter
        add.w   d0,d1                ; offset on screen
        add.l   a1,d1                ; add current screen buffer ptr

        add.w   d2,d2                ; d2 = 2*SDelta
        move.w  d2,d5                ; d5 = 2*SDelta
        swap    d2                   ; d2 high word = 2*SDelta
        sub.w   d3,d5                ; d5 = 2*SDelta - LDelta
        smi     d0                   ; if (2*Sdelta-Ldelta) < 0,
        sub.b   d0,d6                ; add 1 (subtract -1) to octant lookup
        move.b  OctTbl(pc,d6.w),d6   ; look up BLTCON bits for octant
        tst.b   d7
        beq.s   .no_onedot
        ori.w   #$0002,d6            ; ONEDOT: one pixel per row for area-fill outlines
.no_onedot:

        move.w  d5,d2                ; d2 low word = 2*SDelta - LDelta
        sub.w   d3,d2                ; d2 low word = 2*SDelta - 2*LDelta
        asl.w   #6,d3                ; d3 = LDelta << 6
        add.w   #66,d3               ; d3 = BLTSIZE

        ; --- blit ---
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        tst.b   d7
        beq.s   .no_fill_start
        move.w  a3,d0
        bchg    d0,(a2)
.no_fill_start:
        move.w  #$8000,custom.BLTADAT    ; reset: A-channel blits such as area_fill leave stale data here
        move.l  d6,custom.BLTCON0    ; shift, minterm, octant bits
        move.w  d5,custom.BLTAPTL    ; 2*SDelta - LDelta
        move.l  d2,custom.BLTBMOD    ; 2*SDelta | 2*SDelta - 2*LDelta
        move.l  d1,custom.BLTCPT     ; source
        move.l  d1,custom.BLTDPT     ; destination
        move.w  d3,custom.BLTSIZE
.finish:
        movem.l (sp)+,d2-d6/a2-a4
        rts

; Octant lookup table placed right after the rts so it is reachable via
; PC-relative addressing from the move.b OctTbl(PC,d6.w) instruction above.
; Each pair of entries encodes BLTCON1 octant bits for one of the 8 octants.
; First byte has SIGN=0, second has SIGN=1.
OctTbl:
        dc.b 0*4+1,0*4+1+64    ;7
        dc.b 4*4+1,4*4+1+64    ;6
        dc.b 2*4+1,2*4+1+64    ;4
        dc.b 5*4+1,5*4+1+64    ;5
        dc.b 1*4+1,1*4+1+64    ;0
        dc.b 6*4+1,6*4+1+64    ;1
        dc.b 3*4+1,3*4+1+64    ;3
        dc.b 7*4+1,7*4+1+64    ;2
        ; !notreached!
        }}
    }

    ; Cookie-cut a shifted BOB plane: dst = (mask AND src) OR (NOT mask AND dst).
    ; Call once per plane, reusing the same mask. The mask must be non-null;
    ; use copy_rect() for unmasked copies.
    ; width_words is the source row width (tightly packed, no guard word).
    ; Implements the Jones trick: when shift != 0 the blit is one word wider
    ; (width_words+1) with BLTALWM=$0000 and source modulos stepped back by 2,
    ; so the barrel-shift spill lands in the extra destination word instead of
    ; being clipped. No guard word is stored.
    ; For position (x,y), pass dst = bitplane + y*pitch + (x/16)*2 and shift = x & 15.
    ; Each modulo is the channel's row pitch in bytes minus width_words*2. For
    ; tightly packed source and mask rows, use src_mod = mask_mod = 0; the
    ; helper derives the -2 adjustment for shifted blits itself.
    ; The caller must keep every destination word inside the screen, including
    ; the extra spill word when shift != 0. Save/restore buffers must cover
    ; width_words+1 words per row when shifted. Clip top or bottom by adjusting
    ; the row pointers and height. Clip the right edge by reducing width_words
    ; and recomputing the modulos. Left clipping works directly only on word
    ; boundaries: advance src and mask, reduce width_words and set shift = 0.
    ; Other negative x positions need offscreen padding or a dedicated
    ; left-clipping routine.
    ; width_words must be 1..63 when shifted (64 only when shift == 0) and the
    ; expanded blit must fit the destination (dst_mod >= 2 when shifted,
    ; else >= 0).
    ; Waits for the blitter to become idle, then returns after starting the blit.
    asmsub masked_blit(pointer src @A0, pointer mask @A1, pointer dst @A2, uword width_words @D0, uword height @D1, uword src_mod @D2, uword mask_mod @D3, uword dst_mod @D4, ubyte shift @D5) {
        %asm {{
        move.l  a2,-(sp)               ; destination
        movea.l a1,a2                   ; mask
        move.l  a0,-(sp)               ; source
        jsr     blitter.masked_blit_prepare
        movea.l (sp)+,a0               ; source
        movea.l (sp)+,a1               ; destination
        jsr     blitter.masked_blit_start
        rts
        }}
    }

    ; Clear destination pixels where the shifted mask is set: dst = (NOT mask) AND dst.
    ; Clears to zero; does not restore the saved background.
    ; Uses the Jones trick like masked_blit(): when shift != 0 the blit is one
    ; word wider with BLTALWM=$0000 and modulos stepped back by 2, so no guard
    ; word is stored. Save buffers must cover width_words+1 words when shifted.
    ; Uses the same mask, width, modulo, shift and wait conventions as masked_blit().
    asmsub erase(pointer mask @A0, pointer dst @A1, uword width_words @D0, uword height @D1, uword mask_mod @D2, uword dst_mod @D3, ubyte shift @D4) {
        %asm {{
        movem.l d2-d3/d6,-(sp)
        moveq   #0,d6
        move.b  d4,d6
        andi.w  #$000f,d6             ; shift 0..15
        beq.s   .no_shift
        cmpi.w  #64,d0
        bcc.s   .width_ok
        addq.w  #1,d0                ; Jones: one extra word for spill
.width_ok:
        subq.w  #2,d2
        subq.w  #2,d3
        lsl.w   #6,d1
        andi.w  #$003f,d0
        or.w    d0,d1
        ror.w   #4,d6
        or.w    #custom.BC0F_SRCA|custom.BC0F_SRCC|custom.BC0F_DEST|blitter.MINTERM_ERASE,d6
        move.w  d6,d0
        move.w  #$ffff,custom.BLTAFWM
        clr.w   custom.BLTALWM
        bra.s   .wait_blt
.no_shift:
        lsl.w   #6,d1
        andi.w  #$003f,d0
        or.w    d0,d1
        moveq   #0,d0
        or.w    #custom.BC0F_SRCA|custom.BC0F_SRCC|custom.BC0F_DEST|blitter.MINTERM_ERASE,d0
        move.w  #$ffff,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        move.w  #0,custom.BLTCON1
        move.w  d2,custom.BLTAMOD
        move.w  d3,custom.BLTDMOD
        move.w  d3,custom.BLTCMOD
        move.l  a0,custom.BLTAPT
        move.l  a1,custom.BLTCPT
        move.l  a1,custom.BLTDPT
        move.w  d0,custom.BLTCON0
        move.w  d1,custom.BLTSIZE
        movem.l (sp)+,d2-d3/d6
        rts
        }}
    }

    ; Draw a whole BOB with one masked blit per plane, using geometry from its
    ; Mob. planes is the destination bitmap's plane-pointer array, as in
    ; graphics.BitMap. row_bytes is the destination row pitch in bytes.
    ; The Bob descriptor can live anywhere, but its image planes and mask must
    ; be in chip RAM. Destination planes may be separate allocations, as with
    ; playfield.create() or AllocRaster().
    ; The draw position is (mob.x_pos+origin_x, mob.y_pos+origin_y). The routine
    ; derives the destination word and barrel shift; the computed top-left must
    ; be nonnegative, and all destination words must fit in the bitmap. It does not
    ; clip. Width is limited to 64 words (1024 pixels), as for masked_blit.
    ; Source and mask rows are tightly packed, ceil(width/16) words per row.
    ; As with masked_blit, no guard word is stored: a shifted blit writes one
    ; extra destination word (Jones trick) so the right-edge spill is preserved.
    ; A null mask draws opaquely by substituting the static all-ones opaque_mask
    ; above, so the same cookie-cut setup runs unchanged without the client
    ; storing a mask. This is exact only at word-aligned x (x & 15 == 0); use
    ; a real mask at other positions to preserve partially covered edge pixels.
    ; masked_blit_prepare programs shared settings once. The plane loop updates
    ; only the source and destination pointers and BLTSIZE.
    ; Generic routine: for many small fixed-size bobs per frame a tuned inline
    ; blit with hardcoded width/mods pays off.
    ; Waits for the blitter before each plane and returns after starting the last.
    asmsub draw_bob(^^mobs.Bob bob @A0, ^^pointer planes @A1, uword row_bytes @D0) {
        %asm {{
        movem.l d2-d7/a2-a4,-(sp)
        lea     -4(sp),sp            ; destination byte offset for the first plane
        movea.l a1,a4                ; destination plane array
        movea.l mobs.Bob.p8v_mob(a0),a1
        move.w  mobs.Bob.p8v_depth(a0),d6
        movea.l mobs.Bob.p8v_data(a0),a3
        movea.l mobs.Bob.p8v_mask(a0),a0 ; mask, 0 for opaque
        tst.w   d6
        beq     .done
        move.w  mobs.Mob.p8v_width(a1),d2
        beq     .done
        move.w  mobs.Mob.p8v_height(a1),d3
        beq     .done
        add.w   #15,d2
        lsr.w   #4,d2                ; width in words
        cmpi.w  #64,d2
        bls.s   .width_ok
        moveq   #64,d2               ; blitter maximum, the rest cannot be drawn
.width_ok:
        move.w  d2,d7
        add.w   d7,d7
        mulu.w  d3,d7                ; tightly packed source plane size
        move.w  mobs.Mob.p8v_x_pos(a1),d4
        add.w   mobs.Mob.p8v_origin_x(a1),d4
        move.w  mobs.Mob.p8v_y_pos(a1),d5
        add.w   mobs.Mob.p8v_origin_y(a1),d5
        move.w  d5,d1
        mulu.w  d0,d1                ; y * row_bytes
        move.w  d4,d3
        asr.w   #4,d3
        ext.l   d3
        add.l   d3,d3
        add.l   d3,d1
        move.l  d1,(sp)              ; + (x/16)*2
        andi.w  #15,d4               ; shift
        move.w  d0,d1
        move.w  d2,d0
        add.w   d0,d0                ; blit width in bytes
        sub.w   d0,d1                ; dst_mod = row bytes - blit width bytes
        moveq   #0,d0                ; tightly packed source rows
        moveq   #0,d3                ; tightly packed mask rows
        move.l  a0,d5
        bne.s   .have_mask
        lea     blitterchip.opaque_mask,a0
        move.w  d2,d5
        add.w   d5,d5
        neg.w   d5
        move.w  d5,d3                ; the all-ones block repeats every row
.have_mask:
        movea.l a0,a2                ; mask pointer, reset before each plane
        exg     d0,d2                ; d0 = blit width in words, d2 = src_mod
        move.w  d4,d5                ; shift
        move.w  d1,d4                ; dst_mod
        move.w  mobs.Mob.p8v_height(a1),d1
        jsr     blitter.masked_blit_prepare
        move.w  d0,d4                ; BLTSIZE
        move.l  (sp),d1              ; destination byte offset
.plane:
        move.l  (a4)+,a1             ; destination plane
        add.l   d1,a1
        movea.l a3,a0
        move.w  d4,d0
        jsr     blitter.masked_blit_start
        adda.l  d7,a3                ; next source plane
        subq.w  #1,d6
        bne.s   .plane
.done:
        lea     4(sp),sp
        movem.l (sp)+,d2-d7/a2-a4
        rts
        }}
    }

    ; Fill the area between set source bits. The HRM requires inclusive
    ; FILL_OR in descending mode; reading and writing the same data leaves
    ; the outline intact.
    ; mod is the row modulo in bytes (bitplane pitch minus region width).
    ; After drawing a polygon outline with line_draw(..., fill=true),
    ; call this to fill it solid.
    ; The blit width must be <= the destination screen width (modulo >= 0).
    ; Waits for the blitter to become idle, then returns after starting the blit.
    asmsub area_fill(pointer src @A0, uword width_words @D0, uword height @D1, uword mod @D2) {
        %asm {{
        movem.l d4-d5,-(sp)
        ; Descending setup: point at the last fetched word. The hardware
        ; SUBTRACTS the modulos in descending mode (HRM), so the positive
        ; per-row skip is passed unchanged; negating it would push the
        ; pointer down out of the region instead of stepping up each row.
        ; Last fetched word is (h-1)*(w+m)+(w-1): the modulo gap after the
        ; final row is never fetched, so h*(w+m)-1 would overshoot for mod!=0.
        ; mod arrives in bytes (like fill_rect); halve it for word counting.
        move.w  d0,d4
        move.w  d2,d5
        asr.w   #1,d5                ; modulo bytes -> words
        add.w   d5,d4                ; words per row including modulo
        mulu.w  d1,d4                ; h*(w+m)
        sub.w   d5,d4                ; minus trailing modulo gap
        subq.l  #1,d4                ; minus one = last fetched word index
        add.l   d4,d4                ; total bytes
        add.l   d4,a0                ; a0 = last word of region
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        move.l  a0,custom.BLTAPT
        move.l  a0,custom.BLTDPT
        move.w  d2,custom.BLTAMOD
        move.w  d2,custom.BLTDMOD
        move.w  #$ffff,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
        move.l  #(((custom.BC0F_SRCA|custom.BC0F_DEST|blitter.MINTERM_A_ONLY)<<16)|$000a),custom.BLTCON0  ; USEA=1, USED=1, D=A (solid copy)
        lsl.w   #6,d1
        or.w    d0,d1
        move.w  d1,custom.BLTSIZE
        movem.l (sp)+,d4-d5
        rts
        }}
    }

    ; Draw or erase the horizontal span [x1..x2] on scanline y of bitplane dst.
    ; Drawing replaces covered pixels with pattern; erasing clears its set bits.
    ; A vertical blit uses as much DMA as Bresenham line mode, so use line_draw()
    ; for vertical segments too.
    asmsub horiz_line(pointer dst @A1, uword x1 @D0, uword x2 @D2, uword y @D1, uword pattern @D3, bool erase @D5) {
        %asm {{
        movem.l d2-d7/a2-a3,-(sp)
        ext.w   d5
        move.w  d5,a2            ; save erase flag
        ; swap x if needed
        cmp.w   d2,d0
        ble.s   .h_nswap
        exg     d0,d2
.h_nswap:
        ; leftMask -> d6
        move.w  d0,d5
        and.w   #15,d5
        moveq   #-1,d6
        lsr.w   d5,d6
        ; rightMask -> a0 (low word)
        move.w  d2,d5
        and.w   #15,d5
        moveq   #15,d7
        sub.w   d5,d7
        moveq   #-1,d5
        lsl.w   d7,d5
        move.w  d5,a0
        ; leftWord -> d5, rightWord -> d7
        move.w  d0,d5
        lsr.w   #4,d5
        move.w  d2,d7
        lsr.w   #4,d7
        ; row base ptr -> a3
        move.w  blitter.line_mod,d4
        mulu.w  d4,d1            ; y*mod
        move.w  d5,d2
        add.w   d2,d2            ; leftWord*2
        ext.l   d2
        add.l   d2,d1
        lea     (a1,d1.l),a3     ; dst base
        move.w  d3,d4            ; pattern (screen_mod is dead now)
        ; wait for any in-flight blit before touching chip RAM
.h_wait:
        btst.b  #6,custom.DMACONR
        bne.s   .h_wait
        ; same word?
        cmp.w   d5,d7
        bne.s   .h_multi
        ; mask = leftMask & rightMask
        move.w  a0,d2
        and.w   d6,d2
        cmpa.w  #0,a2
        bne.s   .h_same_erase
        ; Draw path: update old with the pattern inside the mask, reading once.
        move.w  (a3),d0
        move.w  d0,d1
        eor.w   d4,d0
        and.w   d2,d0
        eor.w   d1,d0
        move.w  d0,(a3)
        bra     .h_done
.h_same_erase:
        move.w  d4,d0
        and.w   d2,d0            ; pattern & mask
        not.w   d0
        and.w   d0,(a3)
        bra     .h_done
.h_multi:
        ; left edge
        cmpa.w  #0,a2
        bne.s   .h_left_erase
        move.w  (a3),d0
        move.w  d0,d1
        eor.w   d4,d0
        and.w   d6,d0
        eor.w   d1,d0
        move.w  d0,(a3)
        bra.s   .h_left_done
.h_left_erase:
        move.w  d4,d0
        and.w   d6,d0
        not.w   d0
        and.w   d0,(a3)
.h_left_done:
        addq.l  #2,a3
        ; Number of words between the edge words.
        move.w  d7,d0
        sub.w   d5,d0
        subq.w  #1,d0
        ble.s   .h_right
        cmpa.w  #0,a2
        bne.s   .h_mid_erase
        ; middle: fill with the pattern, two longwords per iteration
        move.w  d0,d3
        and.w   #1,d3            ; trailing odd word
        lsr.w   #1,d0            ; longword count
        move.w  d4,d1
        swap    d1
        move.w  d4,d1            ; pattern in both halves
        move.w  d0,d2
        lsr.w   #1,d2            ; longword pairs
        beq.s   .h_mid_odd_long
        subq.w  #1,d2
.h_mid_pairs:
        move.l  d1,(a3)+
        move.l  d1,(a3)+
        dbra    d2,.h_mid_pairs
.h_mid_odd_long:
        btst    #0,d0
        beq.s   .h_mid_single_word
        move.l  d1,(a3)+
.h_mid_single_word:
        tst.w   d3
        beq.s   .h_right
        move.w  d4,(a3)+
        bra.s   .h_right
.h_mid_erase:
        move.w  d4,d1
        not.w   d1
.h_mid_erase_loop:
        and.w   d1,(a3)
        addq.l  #2,a3
        subq.w  #1,d0
        bgt.s   .h_mid_erase_loop
.h_right:
        move.w  a0,d2            ; rightMask
        cmpa.w  #0,a2
        bne.s   .h_right_erase
        move.w  (a3),d0
        move.w  d0,d1
        eor.w   d4,d0
        and.w   d2,d0
        eor.w   d1,d0
        move.w  d0,(a3)
        bra     .h_done
.h_right_erase:
        move.w  d4,d0
        and.w   d2,d0
        not.w   d0
        and.w   d0,(a3)
.h_done:
        movem.l (sp)+,d2-d7/a2-a3
        rts
        }}
    }

    ; First stage of a masked blit. Waits for the blitter, computes BLTSIZE and
    ; programs the control words, modulos and edge masks shared by planes of the
    ; same shape. Returns BLTSIZE in D0 for masked_blit_start().
    ; Used by masked_blit() and draw_bob(); keep their register contracts in sync.
    private asmsub masked_blit_prepare(uword width_words @D0, uword height @D1, uword src_mod @D2, uword mask_mod @D3, uword dst_mod @D4, ubyte shift @D5) -> uword @D0 {
        %asm {{
        movem.l d2-d4/d6,-(sp)
        moveq   #0,d6
        move.b  d5,d6
        andi.w  #$000f,d6             ; shift 0..15
        beq.s   .no_shift
        ; Jones: blit one word wider, rewind source mods, mask last fetch to 0.
        ; The barrel shifter still carries the previous word's spill into the
        ; extra word, while the zeroed low bits select background (C) there,
        ; hiding the rewound next-row fetch in B.
        cmpi.w  #64,d0
        bcc.s   .width_ok             ; 64 cannot expand, keep clipping
        addq.w  #1,d0
.width_ok:
        subq.w  #2,d2
        subq.w  #2,d3
        subq.w  #2,d4
        lsl.w   #6,d1
        andi.w  #$003f,d0            ; hardware encodes width 64 as zero
        or.w    d0,d1                ; d1 = BLTSIZE
        ror.w   #4,d6                ; ASHIFT = shift << 12
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        move.w  d6,custom.BLTCON1
        or.w    #custom.BC0F_SRCA|custom.BC0F_SRCB|custom.BC0F_SRCC|custom.BC0F_DEST|blitter.MINTERM_MASKED,d6
        move.w  d2,custom.BLTBMOD
        move.w  d3,custom.BLTAMOD
        move.w  d4,custom.BLTDMOD
        move.w  d4,custom.BLTCMOD
        move.w  #$ffff,custom.BLTAFWM
        clr.w   custom.BLTALWM
        move.w  d6,custom.BLTCON0
        move.w  d1,d0                ; BLTSIZE for the caller
        movem.l (sp)+,d2-d4/d6
        rts
.no_shift:
        lsl.w   #6,d1
        andi.w  #$003f,d0
        or.w    d0,d1
        moveq   #0,d6                 ; shift 0, keep masks full
.wait_blt2:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt2
        move.w  d6,custom.BLTCON1
        or.w    #custom.BC0F_SRCA|custom.BC0F_SRCB|custom.BC0F_SRCC|custom.BC0F_DEST|blitter.MINTERM_MASKED,d6
        move.w  d2,custom.BLTBMOD
        move.w  d3,custom.BLTAMOD
        move.w  d4,custom.BLTDMOD
        move.w  d4,custom.BLTCMOD
        move.w  #$ffff,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
        move.w  d6,custom.BLTCON0
        move.w  d1,d0
        movem.l (sp)+,d2-d4/d6
        rts
        }}
    }

    ; Second stage of a masked blit. Waits for the blitter, reloads the mask and
    ; data pointers, then starts the blit. Reset the mask pointer because the
    ; blitter advances it while drawing each plane.
    private asmsub masked_blit_start(pointer src @A0, pointer dst @A1, pointer mask @A2, uword bltsize @D0) {
        %asm {{
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        move.l  a2,custom.BLTAPT
        move.l  a0,custom.BLTBPT
        move.l  a1,custom.BLTCPT
        move.l  a1,custom.BLTDPT
        move.w  d0,custom.BLTSIZE
        rts
        }}
    }

}

blitterchip {
    ; The blitter fetches this data with DMA, so it must live in chip RAM.
    %option no_symbol_prefixing, ignore_unused, amiga_chipram
    ; Synthetic all-ones mask so a Bob with no client mask can still reuse the
    ; same Jones cookie-cut helpers: draw_bob points channel A here when
    ; Bob.mask is null. Shifted blits re-read the same row via a negative
    ; modulo, so 64 words covers the widest shifted blit.
    private uword[64] opaque_mask = [
        $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff,
        $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff,
        $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff,
        $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff,
        $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff,
        $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff,
        $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff,
        $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff, $ffff
    ]
}
