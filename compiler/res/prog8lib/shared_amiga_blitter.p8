%import custom

blitter {
    %option merge, no_symbol_prefixing, ignore_unused

    private uword @shared line_pattern
    private uword @shared line_mod

    ; blitter - bare-metal blitter programming helpers: rect copies, fills,
    ; masked blits and line drawing with the MINTERM logic modes below.
    ; No OS calls; safe after custom.grab_system(). CPU and blitter run
    ; concurrently by design (see WAITING BEHAVIOR below).

    ; WARNING: All pointers passed to blitter routines MUST point to CHIP RAM.
    ; The Amiga blitter hardware can only access CHIP RAM directly.
    ; Using fast RAM pointers will result in bus errors or corrupted data.
    ;
    ; WIDTH LIMITATION: The blitter can only handle 64 words (1024 pixels) width per operation.
    ; Standard Amiga screens (320/640 pixels) fit within this limit.
    ; For wider images, split the blit into multiple operations manually.
    ;
    ; WAITING BEHAVIOR: Operations wait for the blitter to become idle before
    ; programming its registers or accessing target chip memory. CPU-only setup
    ; may overlap a previous blit. Calls return after triggering, not completion.
    ; Call blitter.wait() explicitly when you need to ensure the blitter has completed
    ; (e.g., before reading back modified memory or swapping display buffers).

    const uword MINTERM_MASKED = $CA    ; (A AND B) OR (NOT A AND C) - masked sprite blit
    const uword MINTERM_A_ONLY = $F0    ; channel A only (same as COPY)
    const uword MINTERM_NOT_A = $0F     ; NOT A - invert source
    const uword MINTERM_B_ONLY = $CC    ; channel B only
    const uword MINTERM_A_XOR_C = $5A   ; A XOR C (erase/restore patterns)
    const uword MINTERM_MASKED_OR = $EA ; C OR (A AND B) - additive overlay with mask
    const uword MINTERM_OR = $FA        ; A OR C - simple overlay, no mask
    const uword MINTERM_ERASE = $0A     ; (NOT A) AND C - erase where source is set
    const uword MINTERM_IDENTITY = $AA  ; C - no-op (used with BLTCDAT for fills)
    const uword MINTERM_CLEAR = $00     ; 0 - clear destination (all zeros)
    const uword MINTERM_SET = $FF       ; 1 - set destination (all ones)
    const uword MINTERM_AND = $3C       ; (A AND B) AND C - keep where all three set
    const uword MINTERM_COND = $AC      ; (A AND C) OR (B AND NOT C) - conditional replace

    asmsub wait() {
        ; Wait until the blitter is completely idle.
        ; Use this before reading back blitter-modified memory or swapping buffers.
        %asm {{
.busy:
            btst.b  #6,custom.DMACONR
            bne.s   .busy
            rts
        }}
    }

    asmsub set_blitpri(bool hasprio @D0) {
        ; Set or clear blitter priority over the CPU.
        ; When enabled, the blitter gets bus priority and completes faster,
        ; but the CPU runs slower during the blit.
        ; Disable when you need full CPU speed while the blitter runs.
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

    ; Copy a rectangular block from src to dst.
    ; Only channels A and D are set up, so minterm must be A-only:
    ; MINTERM_A_ONLY (copy), MINTERM_NOT_A (invert), MINTERM_CLEAR,
    ; MINTERM_SET. Minterms referencing B or C read unset channels.
    ; Waits for blitter idle before programming it, returns immediately after triggering the blit.
    asmsub copy_rect(pointer src @A0, pointer dst @A1, uword width_words @D0, uword height @D1, uword src_mod @D2, uword dst_mod @D3, uword minterm @D4, ubyte descending @D5) {
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

    ; Fill a rectangular block with a pattern.
    ; Waits for blitter idle before programming it, returns immediately after triggering the blit.
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
    ; pixel: line pixel pattern in BLTADAT (usually $8000 for single pixel)
    ; pattern: line fill pattern in BLTBDAT (usually $ffff for solid lines)
    ; screen_mod: bytes per row of the destination bitplane (pitch)
    ; Waits for blitter idle at start, returns immediately after setup.
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

    ; Draw a line in an exact pen color on a multi-plane bit-mapped screen.
    ; For every bitplane of the bitmap, the plane's pixels along the line are
    ; drawn (set) when that bit of the pen is on, or erased (cleared) when it
    ; is off, so the line's pixels get exactly the given color without OR-ing
    ; with anything that was underneath. Needs line_init() once beforehand
    ; with the bitmap's BytesPerRow.
    ; planes must point to an array of at least depth 32-bit plane base
    ; addresses. With a graphics.BitMap, pass `&bitmap.Planes as ^^pointer`;
    ; with hand-allocated chip memory, pass a pointer to your plane table.
    ; No BitMap required, so bare-metal callers can pass a hand-built array.
    sub line_color(uword x1, uword y1, uword x2, uword y2, ubyte pen, ubyte depth, ^^pointer planes) {
        for plane in 0 to depth-1 {
            bool isSet = ((pen >> plane) & 1) != 0
            if isSet
                line_draw(x1, y1, x2, y2, planes[plane], false, false)
            else
                line_draw(x1, y1, x2, y2, planes[plane], true, false)
        }
    }

    ; Draw a single line using the blitter's hardware Bresenham line mode.
    ; Must be preceded by a call to line_init() with the appropriate parameters.
    ; Uses the octant-lookup-table algorithm from coppershade.org
    ; (see https://coppershade.org/asmskool/SOURCES/Developing-Demo-Effects/DDE4/LineVectors/).
    ; x1, y1: starting coordinates (absolute, any direction supported)
    ; x2, y2: ending coordinates
    ; screen_ptr: pointer to bitplane memory (CHIP RAM)
    ; erase: clear the line's pixels instead of setting them
    ; fill: draw a top-to-bottom XOR boundary with ONEDOT set; horizontal
    ;   edges are skipped and should be drawn after area_fill. fill overrides erase.
    ; Waits for blitter idle before programming it, returns immediately after triggering the blit.
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
        ; CPU setup (these would normally be done once in LineInit and
        ; preserved across calls; we set them up here for self-containment).
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
        move.w  #$8000,custom.BLTADAT    ; reseed: A-channel blits (e.g. area_fill) leave garbage here
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

    ; Waits for blitter idle before programming it, returns immediately after triggering the blit.
    asmsub masked_blit(pointer src @A0, pointer mask @A1, pointer dst @A2, uword width_words @D0, uword height @D1, uword src_mod @D2, uword mask_mod @D3, uword dst_mod @D4) {
        %asm {{
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        move.w  d2,custom.BLTBMOD
        move.w  d3,custom.BLTAMOD
        move.w  d4,custom.BLTDMOD
        move.w  d4,custom.BLTCMOD
        move.w  #$ffff,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
        move.l  a1,custom.BLTAPT
        move.l  a0,custom.BLTBPT
        move.l  a2,custom.BLTCPT
        move.l  a2,custom.BLTDPT
        move.l  #(((custom.BC0F_SRCA|custom.BC0F_SRCB|custom.BC0F_SRCC|custom.BC0F_DEST|blitter.MINTERM_MASKED)<<16)|$0800),custom.BLTCON0
        lsl.w   #6,d1
        or.w    d0,d1
        move.w  d1,custom.BLTSIZE
        rts
        }}
    }

    ; Clear a rectangular region of a bitplane to zero.
    ; Waits for blitter idle at start, returns immediately after triggering the blit.

    ; Fill an entire bitplane (or any width_words x height block) with a 16-bit pattern.
    ; pattern: the value written to every word of the destination (e.g. $0000 clears,
    ;          $ffff fills solid, $aaaa produces a vertical stripe pattern).
    ; Waits for blitter idle at start, returns immediately after triggering the blit.

    ; Hardware area fill: fill all scanlines between set source bits.
    ; Inclusive fill (FILL_OR) in descending mode, as the HRM requires;
    ; read-modify-write with source = destination, outlines left intact.
    ; mod is the row modulo in bytes (bitplane pitch minus region width).
    ; After drawing a polygon outline with line_draw(..., fill=true),
    ; call this to fill it solid.
    ; Waits for blitter idle before programming it, returns immediately after triggering.
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
    ; Draws/erases a horizontal span [x1..x2] on scanline y of the bitplane dst.
    ; Draw replaces covered pixels with pattern; erase clears pattern bits.
    ; (No faster vertical-line helper exists: a vertical blit has the same DMA
    ; traffic as the Bresenham line mode, so vertical segments stay on line_draw.)
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
        ; draw: old ^ ((old ^ pattern) & mask) reads the word once
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
        ; middle count
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

}

