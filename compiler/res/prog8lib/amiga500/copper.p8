; Copper list builder and manager for Amiga custom hardware.
; IMPORTANT: Copper lists MUST be allocated in CHIP RAM (accessible by both CPU and custom chips).
; Use memory() or ensure your list buffers are in CHIP RAM range.

%import custom

copper {
    %option no_symbol_prefixing, ignore_unused

    ; Current write position in copper list (used by assembly routines)
    pointer @shared _copper_pos

    ; ========== List building routines (assembly) ==========

    asmsub init(pointer list_ptr @A0) clobbers (A0) {
        %asm {{
            move.l  a0,copper._copper_pos
            rts
        }}
    }

    asmsub move(uword reg_addr @D0, uword value @D1) clobbers (D0, D1, A0) {
        ; Add a MOVE instruction to the copper list.
        ; reg_addr is the custom register address offsetr (e.g., $0180 for COLOR00 / $dff180).
        ; The copper MOVE instruction encodes the register offset from $dff000.
        %asm {{
            move.l  copper._copper_pos,a0
            and.w   #$1fe,d0          ; mask to even register offset (bit 0 must be 0)
            move.w  d0,(a0)+          ; word 1: register offset (bit 0 = 0 identifies MOVE)
            move.w  d1,(a0)+          ; word 2: value
            move.l  a0,copper._copper_pos
            rts
        }}
    }

    asmsub wait(ubyte vpos @D0, ubyte hpos @D1, uword compare_mask @D2) clobbers (D0, D1, D2, A0) {
        ; Add a WAIT instruction to the copper list.
        ; Word1 = (vpos<<8) | (hpos<<1) | 1   (bit 0 = 1 identifies WAIT/SKIP)
        ; Word2 = compare_mask with bit 0 = 0 (distinguishes WAIT from SKIP).
        ;   Word2 bit 15   = BFD (1 = no blitter wait, 0 = also wait for blitter)
        ;   Word2 bits 14-8 = VE (vertical compare enable mask)
        ;   Word2 bits 7-1  = HE (horizontal compare enable mask)
        %asm {{
            move.l  copper._copper_pos,a0
            and.w   #$ff,d0           ; zero-extend vpos to word
            lsl.w   #8,d0             ; vpos to high byte
            and.w   #$ff,d1           ; zero-extend hpos to word
            lsl.w   #1,d1             ; hpos to bit 1
            or.w    d1,d0             ; combine vpos and hpos
            or.w    #1,d0             ; set bit 0 to identify instruction as WAIT/SKIP
            move.w  d0,(a0)+          ; word 1
            and.w   #$fffe,d2         ; clear bit 0 to indicate WAIT (not SKIP)
            move.w  d2,(a0)+          ; word 2
            move.l  a0,copper._copper_pos
            rts
        }}
    }

    asmsub skip(ubyte vpos @D0, ubyte hpos @D1, uword compare_mask @D2) clobbers (D0, D1, D2, A0) {
        ; Add a SKIP instruction to the copper list.
        ; Word1 = (vpos<<8) | (hpos<<1) | 1   (bit 0 = 1 identifies WAIT/SKIP)
        ; Word2 = compare_mask with bit 0 = 1 (distinguishes SKIP from WAIT).
        %asm {{
            move.l  copper._copper_pos,a0
            and.w   #$ff,d0           ; zero-extend vpos to word
            lsl.w   #8,d0             ; vpos to high byte
            and.w   #$ff,d1           ; zero-extend hpos to word
            lsl.w   #1,d1             ; hpos to bit 1
            or.w    d1,d0             ; combine vpos and hpos
            or.w    #1,d0             ; set bit 0 to identify instruction as WAIT/SKIP
            move.w  d0,(a0)+          ; word 1
            or.w    #1,d2             ; set bit 0 to indicate SKIP (not WAIT)
            move.w  d2,(a0)+          ; word 2
            move.l  a0,copper._copper_pos
            rts
        }}
    }

    asmsub end() clobbers (A0) {
        ; Terminate the copper list with the end marker ($FFFF, $FFFE).
        %asm {{
            move.l  copper._copper_pos,a0
            move.w  #$ffff,(a0)+      ; end marker word 1
            move.w  #$fffe,(a0)+      ; end marker word 2
            move.l  a0,copper._copper_pos
            rts
        }}
    }

    asmsub get_pos() -> pointer @A0 {
        ; Return the current write position in the copper list.
        ; Useful for calculating list size or continuing from a saved position.
        %asm {{
            move.l  copper._copper_pos,a0
            rts
        }}
    }

    ; ========== Activation routines (assembly) ==========

    asmsub start(pointer list_ptr @A0) clobbers (A0) {
        ; Activate a copper list by loading its address into COP1LC and enabling Copper DMA.
        ; The copper list must be in CHIP RAM.
        %asm {{
            move.l  a0,custom.COP1LC    ; load copper list pointer (auto-increments)
            move.w  #$8280,custom.DMACON ; DMACON: SET(0x8000) | DMAEN(0x0200) | COPEN(0x0080)
            clr.w   custom.COPJMP1      ; strobe COPJMP1 to activate
            rts
        }}
    }

    ; ========== Effects routines (Prog8) ==========

    sub append_raster_colors(ubyte vpos_start, ^^uword colors, ubyte num_lines) {
        ; Append WAIT+MOVE instructions to change background color per scanline.
        ; Does NOT call init() or end() - caller must handle list setup.
        ;
        ; Parameters:
        ;   vpos_start  - starting vertical beam position (0-255)
        ;   colors      - pointer to array of color values (12-bit RGB, e.g., $0F00 for red)
        ;   num_lines   - number of scanlines to color
        ;
        ; Example:
        ;   copper.init(my_list)
        ;   ... add other copper instructions ...
        ;   copper.append_raster_colors(50, bar_colors, 8)
        ;   copper.end()

        ubyte vpos = vpos_start
        ubyte i
        for i in 0 to num_lines - 1 {
            wait(vpos, 0, $fffe)
            move($180, colors[i])
            vpos += 1
        }
    }
}
