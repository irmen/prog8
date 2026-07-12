sys {
    %option merge, ignore_unused

; ------- memory routines --------

    asmsub memcopy(long source @D0, long tgt @D1, uword count @D2) {
        ; Copy bytes from source to target.
        ; Bulk of the work uses long-aligned longword copies;
        ; pre-alignment and remainder bytes use individual byte copies.
        %asm {{
            movea.l  d0,a0
            movea.l  d1,a1
            move.w   d2,d2
            beq      .done

            ; check if both addresses share the same alignment offset
            move.l   a0,d0
            move.l   a1,d3
            eor.l    d3,d0
            btst     #1,d0
            bne      .bytes

            ; same alignment: pre-align both to a long boundary
            move.l   a0,d3
            and.w    #3,d3             ; d3 = offset (0-3)
            beq      .bulk
            move.w   d3,d0             ; d0 = bytes to align
            cmp.w    d2,d0
            bls      .pre
            move.w   d2,d0             ; cap at count left

.pre:
            subq.w   #1,d0
.loop_pre:
            move.b   (a0)+,(a1)+
            subq.w   #1,d2             ; track remaining count
            dbra     d0,.loop_pre
            beq      .done

.bulk:
            move.w   d2,d3
            move.w   d3,d0
            and.w    #3,d0             ; d0 = remainder 0-3 bytes after longwords
            lsr.w    #2,d3             ; d3 = longword pairs
            beq      .rem
            subq.w   #1,d3
.loop_l:
            move.l   (a0)+,(a1)+
            dbra     d3,.loop_l

.rem:
            subq.w   #1,d0
            bmi      .done
.loop_rem:
            move.b   (a0)+,(a1)+
            dbra     d0,.loop_rem
.done:
            rts

.bytes:
            subq.w   #1,d2
.loop_b:
            move.b   (a0)+,(a1)+
            dbra     d2,.loop_b
            rts
        }}
    }

    asmsub memset(long mem @D0, uword numbytes @D1, ubyte value @D2) {
        %asm {{
            movea.l  d0,a0
            tst.w    d1
            beq      .done
            tst.b    d2
            beq      sys.memclear       ; tail-call: value==0

            ; save fill byte zero-extended
            moveq    #0,d3
            move.b   d2,d3

            ; align address to even for word/longword transfers
            move.l   a0,d0
            btst     #0,d0
            beq      .aligned
            move.b   d3,(a0)+
            subq.l   #1,d1
            beq      .done

.aligned:
            ; expand fill byte to all byte positions in d3
            move.l   d3,d0
            lsl.l    #8,d0
            or.l     d0,d3
            move.l   d3,d0
            swap     d0
            or.l     d0,d3

            ; longword fill (4 bytes per iteration)
            move.l   d1,d0
            lsr.l    #2,d0
            beq      .bytes
            subq.w   #1,d0
.loop_l:
            move.l   d3,(a0)+
            dbra     d0,.loop_l

            ; remaining bytes (0-3)
.bytes:
            and.w    #3,d1
            beq      .done
            subq.w   #1,d1
.loop_b:
            move.b   d3,(a0)+
            dbra     d1,.loop_b
.done:
            rts
        }}
    }

    asmsub memclear(long mem @D0, uword numbytes @D1) {
        ; Clear memory (fill with zero).  Uses movem.l d0-d7,-(a0)
        ; for the bulk clear (32 bytes per instruction, decrementing from
        ; the end address).  Handles misaligned start and remaining bytes.
        ; All instructions are plain 68000-compatible.
        %asm {{
            movea.l  d0,a0
            moveq    #0,d0
            move.w   d1,d0           ; d0 = numbytes (zero-extended)
            beq      .done

            ; align to even for word/longword access
            move.l   a0,d1
            btst     #0,d1
            beq      .compute
            clr.b    (a0)+
            subq.l   #1,d0
            beq      .done

.compute:
            move.l   d0,d1
            move.l   d1,d2
            and.w    #31,d2          ; d2 = tail bytes after 32-byte blocks
            lsr.l    #5,d1           ; d1 = number of 32-byte blocks
            move.l   d2,a2           ; a2 = tail bytes
            movea.l  d1,a1           ; a1 = block count (save before zeroing regs)

            ; point a0 past the last byte, so we can decrement with movem
            move.l   d1,d3
            lsl.l    #5,d3
            adda.l   d3,a0           ; a0 = end address

            ; zero all data registers except d7 (d7 will be the loop counter, not in movem)
            move.l   d1,d7           ; d7 = block count (save before zeroing)
            moveq    #0,d0
            move.l   d0,d1
            move.l   d0,d2
            move.l   d0,d3
            move.l   d0,d4
            move.l   d0,d5
            move.l   d0,d6
            movea.l  d0,a1           ; also zero a1 (part of movem)

            ; bulk clear: 32 bytes per movem (d0-d6=28 bytes + a1=4 bytes), decrementing a0
            subq.w   #1,d7
            bmi      .tail
.loop_m:
            movem.l  d0-d6/a1,-(a0)
            dbra     d7,.loop_m

.tail:
            movea.l  a2,a3
            move.l   a3,d0           ; copy to data reg for shift/btst
            beq      .done
            move.l   d0,d1
            lsr.l    #2,d1           ; d1 = longwords in tail
            beq      .tail_words
            subq.l   #1,d1
.loop_l:
            clr.l    (a0)+
            subq.l   #1,d1
            bpl      .loop_l

.tail_words:
            btst     #1,d0
            beq      .tail_byte
            clr.w    (a0)+

.tail_byte:
            btst     #0,d0
            beq      .done
            clr.b    (a0)+
.done:
            rts
        }}
    }

    asmsub memsetw(long mem @D0, uword numwords @D1, uword value @D2) {
        ; Fill memory with the given 16-bit word value, for the given number of words.
        ; Word-aligned start: fast path with longword stores (2 words at a time).
        ; Odd start: first byte written individually to realign, then same fast path
        ; with byte-swapped fill to keep the pattern correct, plus trailing byte.
        %asm {{
            movea.l  d0,a0
            moveq    #0,d0
            move.w   d1,d0           ; d0 = numwords (zero-extended)
            beq      .done
            move.w   d2,d1           ; d1 = fill word value

            move.l   a0,d3
            btst     #0,d3
            beq      .aligned

            ; odd start: write high byte to align to even
            move.b   d1,(a0)+
            subq.l   #1,d0
            blt      .done
            ; swap fill bytes so aligned fills continue the pattern correctly
            move.w   d1,d2
            swap     d2              ; d2 = fill word, bytes swapped
            moveq    #1,d3           ; flag: odd start (need trailing byte)
            bra      .setup

.aligned:
            move.w   d1,d2           ; d2 = fill word as-is
            moveq    #0,d3           ; flag: even start (no trailing byte)

.setup:
            move.l   d2,d4
            swap     d4
            move.w   d2,d4           ; d4 = d2 : d2  (longword fill pattern)

            ; longword fill (2 words at a time)
            move.l   d0,d5
            lsr.l    #1,d5           ; d5 = numwords / 2
            beq      .odd_word
            subq.w   #1,d5
.loop_l:
            move.l   d4,(a0)+
            dbra     d5,.loop_l

.odd_word:
            btst     #0,d0
            beq      .trailing
            move.w   d2,(a0)+

.trailing:
            tst.b    d3
            beq      .done
            move.b   d1,(a0)         ; original value's low byte to complete last word
.done:
            rts
        }}
    }

}
