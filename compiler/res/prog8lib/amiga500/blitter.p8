%import custom

blitter {
    %option no_symbol_prefixing, ignore_unused

    ; WARNING: All pointers passed to blitter routines MUST point to CHIP RAM.
    ; The Amiga blitter hardware can only access CHIP RAM directly.
    ; Using fast RAM pointers will result in bus errors or corrupted data.
    ;
    ; WIDTH LIMITATION: The blitter can only handle 64 words (1024 pixels) width per operation.
    ; Standard Amiga screens (320/640 pixels) fit within this limit.
    ; For wider images, split the blit into multiple operations manually.
    ;
    ; WAITING BEHAVIOR: All blitter operations wait for the blitter to become idle
    ; at the START of the call, but do NOT wait for the operation to finish before
    ; returning. This allows the CPU to continue working while the blitter runs.
    ; Call blitter.wait() explicitly when you need to ensure the blitter has completed
    ; (e.g., before reading back modified memory or swapping display buffers).

    const uword MINTERM_MASKED = $CA    ; (A AND B) OR (NOT A AND C) - masked sprite blit
    const uword MINTERM_A_ONLY = $F0    ; channel A only (same as COPY)
    const uword MINTERM_B_ONLY = $CC    ; channel B only
    const uword MINTERM_A_XOR_C = $5A   ; A XOR C (erase/restore patterns)

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

    asmsub set_blitpri(bool hasprio @D0) clobbers (D0) {
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
    ; Waits for blitter idle at start, returns immediately after triggering the blit.
    asmsub copy_rect(pointer src @A0, pointer dst @A1, uword width_words @D0, uword height @D1, uword src_mod @D2, uword dst_mod @D3, uword minterm @D4, ubyte descending @D5) clobbers (D0,D1,D2,D3,D4,D5,A0,A1) {
        %asm {{
        movem.l d2-d7/a2-a6,-(sp)
        move.w  d0,d6           ; d6 = width
        move.w  d1,d7           ; d7 = height
        move.w  d2,d2           ; src_mod
        move.w  d3,d3           ; dst_mod
        bsr     .wait_blt
        tst.w   d5
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
        move.w  d2,custom.BLTAMOD
        move.w  d3,custom.BLTDMOD
        move.w  #$ffff,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
        move.l  a0,custom.BLTAPT
        move.l  a1,custom.BLTDPT
        move.w  d4,d1
        or.w    #$0900,d1
        move.w  d1,custom.BLTCON0
        moveq   #0,d1
        tst.w   d5
        beq.s   .nodesc
        move.w  #$0002,d1
.nodesc:
        or.w    #$0800,d1       ; B32: AGA 32-bit blit mode
        move.w  d1,custom.BLTCON1
        move.w  d7,d1
        lsl.w   #6,d1
        or.w    d6,d1
        move.w  d1,custom.BLTSIZE
        movem.l (sp)+,d2-d7/a2-a6
        rts
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        rts
        }}
    }

    ; Fill a rectangular block with a pattern.
    ; Waits for blitter idle at start, returns immediately after triggering the blit.
    asmsub fill_rect(pointer dst @A0, uword width_words @D0, uword height @D1, uword pattern @D2, uword modulo @D3) clobbers (D0,D1,D2,D3,A0) {
        %asm {{
        movem.l d2-d7/a2-a6,-(sp)
        bsr     .wait_blt
        move.w  d3,custom.BLTDMOD
        move.w  #$ffff,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
        move.w  d2,custom.BLTCDAT
        move.l  a0,custom.BLTDPT
        move.w  #$01aa,custom.BLTCON0  ; USED=1, USEC=0, minterm=$AA (BLTCDAT -> D)
        move.w  #$0800,custom.BLTCON1 ; B32: AGA 32-bit blit mode
        move.w  d1,d1
        lsl.w   #6,d1
        or.w    d0,d1
        move.w  d1,custom.BLTSIZE
        movem.l (sp)+,d2-d7/a2-a6
        rts
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        rts
        }}
    }

    ; Initialize the blitter for fast line drawing.
    ; Call this once before drawing a batch of lines with the same parameters.
    ; mask: first/last word mask (usually $ffff for full-word lines)
    ; pixel: line pixel pattern in BLTADAT (usually $8000 for single pixel)
    ; pattern: line fill pattern in BLTBDAT (usually $ffff for solid lines)
    ; screen_mod: bytes per row of the destination bitplane (pitch)
    ; Waits for blitter idle at start, returns immediately after setup.
    asmsub line_init(uword mask @D0, uword pixel @D1, uword pattern @D2, uword screen_mod @D4) clobbers (D0,D1,D2,D4,A0,A1) {
        %asm {{
        movem.l d2-d7/a2-a6,-(sp)
        bsr     .wait_blt
        move.w  d0,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
        move.w  d1,custom.BLTADAT
        move.w  d2,custom.BLTBDAT
        move.w  d4,custom.BLTCMOD
        movem.l (sp)+,d2-d7/a2-a6
        rts
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        rts
        }}
    }

    ; Draw a single line using the blitter's hardware Bresenham line mode.
    ; Must be preceded by a call to line_init() with the appropriate parameters.
    ; Uses the octant-lookup-table algorithm from coppershade.org
    ; (see https://coppershade.org/asmskool/SOURCES/Developing-Demo-Effects/DDE4/LineVectors/).
    ; x1, y1: starting coordinates (absolute, any direction supported)
    ; x2, y2: ending coordinates
    ; screen_mod: bytes per row of the destination bitplane (pitch)
    ; screen_ptr: pointer to bitplane memory (CHIP RAM)
    ; Waits for blitter idle at start, returns immediately after triggering the blit.
    asmsub line_draw(uword x1 @D0, uword y1 @D1, uword x2 @D2, uword y2 @D3, uword screen_mod @D4, pointer screen_ptr @A1) clobbers (D0,D1,D2,D3,D5,D6,A0,A1) {
        %asm {{
        movem.l d2-d7/a2-a6,-(sp)

        ; CPU setup (these would normally be done once in LineInit and
        ; preserved across calls; we set them up here for self-containment).
        move.w  #1*64+2,a3           ; add-value for BLTSIZE
        move.l  #$bfa0000f,a4        ; minterm+mask for quick-rol

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

        move.w  d5,d2                ; d2 low word = 2*SDelta - LDelta
        sub.w   d3,d2                ; d2 low word = 2*SDelta - 2*LDelta
        asl.w   #6,d3                ; d3 = LDelta << 6
        add.w   a3,d3                ; d3 = BLTSIZE

        ; --- blit ---
        bsr     .wait_blt
        move.l  d6,custom.BLTCON0    ; shift, minterm, octant bits
        move.w  d5,custom.BLTAPTL    ; 2*SDelta - LDelta
        move.l  d2,custom.BLTBMOD    ; 2*SDelta | 2*SDelta - 2*LDelta
        move.l  d1,custom.BLTCPT     ; source
        move.l  d1,custom.BLTDPT     ; destination
        move.w  d3,custom.BLTSIZE
        movem.l (sp)+,d2-d7/a2-a6
        rts

.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        rts

; Octant lookup table placed right after the rts so it is reachable via
; PC-relative addressing from the move.b OctTbl(PC,d6.w) instruction above.
; Each pair of entries encodes BLTCON1 octant bits for one of the 8 octants.
; First byte has SING=0, second has SING=1 (descending mode).
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

    ; Masked blit: copy src to dst using a mask channel.
    ; Waits for blitter idle at start, returns immediately after triggering the blit.
    asmsub masked_blit(pointer src @A0, pointer mask @A1, pointer dst @A2, uword width_words @D0, uword height @D1, uword src_mod @D2, uword mask_mod @D3, uword dst_mod @D4) clobbers (D0,D1,D2,D3,D4,A0,A1,A2) {
        %asm {{
        movem.l d2-d7/a2-a6,-(sp)
        bsr     .wait_blt
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
        move.w  #$0fca,custom.BLTCON0
        move.w  #$0800,custom.BLTCON1 ; B32: AGA 32-bit blit mode
        move.w  d1,d1
        lsl.w   #6,d1
        or.w    d0,d1
        move.w  d1,custom.BLTSIZE
        movem.l (sp)+,d2-d7/a2-a6
        rts
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        rts
        }}
    }

    ; Clear a rectangular region of a bitplane to zero.
    ; Waits for blitter idle at start, returns immediately after triggering the blit.
    asmsub clear_plane(pointer dst @A0, uword width_words @D0, uword height @D1) clobbers (D0,D1,A0) {
        %asm {{
        movem.l d2-d7/a2-a6,-(sp)
        bsr     .wait_blt
        move.w  #$ffff,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
        move.w  #$0000,custom.BLTCDAT
        move.w  #$0000,custom.BLTDMOD
        move.l  a0,custom.BLTDPT
        move.w  #$01aa,custom.BLTCON0
        move.w  #$0800,custom.BLTCON1 ; B32: AGA 32-bit blit mode
        move.w  d1,d1
        lsl.w   #6,d1
        or.w    d0,d1
        move.w  d1,custom.BLTSIZE
        movem.l (sp)+,d2-d7/a2-a6
        rts
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        rts
        }}
    }

    ; Fill an entire bitplane (or any width_words x height block) with a 16-bit pattern.
    ; pattern: the value written to every word of the destination (e.g. $0000 clears,
    ;          $ffff fills solid, $aaaa produces a vertical stripe pattern).
    ; Waits for blitter idle at start, returns immediately after triggering the blit.
    asmsub fill_plane(pointer dst @A0, uword width_words @D0, uword height @D1, uword pattern @D2) clobbers (D0,D1,D2,A0) {
        %asm {{
        movem.l d2-d7/a2-a6,-(sp)
        bsr     .wait_blt
        move.w  #$ffff,custom.BLTAFWM
        move.w  #$ffff,custom.BLTALWM
        move.w  d2,custom.BLTCDAT
        move.l  a0,custom.BLTDPT
        move.w  #$01aa,custom.BLTCON0  ; USED=1, USEC=0, minterm=$AA (BLTCDAT -> D)
        move.w  #$0800,custom.BLTCON1 ; B32: AGA 32-bit blit mode
        move.w  #$0000,custom.BLTDMOD
        move.w  d1,d1
        lsl.w   #6,d1
        or.w    d0,d1
        move.w  d1,custom.BLTSIZE
        movem.l (sp)+,d2-d7/a2-a6
        rts
.wait_blt:
        btst.b  #6,custom.DMACONR
        bne.s   .wait_blt
        rts
        }}
    }
}
