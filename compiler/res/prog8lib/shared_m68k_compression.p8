compression {

    %option ignore_unused, merge


    asmsub decode_rle(^^ubyte compressed @A0, ^^ubyte target @A1, uword maxsize @D0) clobbers (A1, D0, D1, D2) -> ^^ubyte @A0 {
        ; -- Decodes "ByteRun1" (aka PackBits) RLE compressed data. Control byte value 128 ends the decoding.
        ;    Also stops decompressing if the maxsize has been reached.
        ;    Returns the size of the decompressed data.
        %asm {{
.next_packet:
                    tst.w   d0
                    ble.s   .done
                    move.b  (a0)+,d1
                    ext.w   d1
                    bpl.s   .literal
                    cmpi.b  #$80,d1
                    beq.s   .next_packet

                    ; Repeat run: repeat the next byte 1-n times.
                    add.w   d1,d0
                    subq.w  #1,d0
                    neg.w   d1
                    move.b  (a0)+,d2
.repeat:
                    move.b  d2,(a1)+
                    dbra    d1,.repeat
                    bra.s   .next_packet

.literal:
                    ; Literal run: copy n+1 bytes verbatim.
                    sub.w   d1,d0
                    subq.w  #1,d0
.copy:
                    move.b  (a0)+,(a1)+
                    dbra    d1,.copy
                    bra.s   .next_packet

.done:
                    rts
        }}
    }


    asmsub unZX0(pointer compressed @A0, pointer output @A1) {
        %asm {{

;  unzx0_68000.s - ZX0 decompressor for 68000   (raw, headerless data)
;
; platon42: Modified to not preserve registers [irmen: changed this again...] and to not use long word operations with
; unlikely and unsupported block lengths > 64 KB. get_elias inlined for speed and other
; optimizations.
;
;  in:  a0 = start of compressed data
;       a1 = start of decompression buffer
;
;  out: a0 = end of compressed data
;       a1 = end of decompression buffer
;
;  Copyright (C) 2021 Emmanuel Marty
;  Copyright (C) 2023 Emmanuel Marty, Chris Hodges
;  ZX0 compression (c) 2021 Einar Saukas, https://github.com/einar-saukas/ZX0
;
;  This software is provided 'as-is', without any express or implied
;  warranty.  In no event will the authors be held liable for any damages
;  arising from the use of this software.
;
;  Permission is granted to anyone to use this software for any purpose,
;  including commercial applications, and to alter it and redistribute it
;  freely, subject to the following restrictions:
;
;  1. The origin of this software must not be misrepresented; you must not
;     claim that you wrote the original software. If you use this software
;     in a product, an acknowledgment in the product documentation would be
;     appreciated but is not required.
;  2. Altered source versions must be plainly marked as such, and must not be
;     misrepresented as being the original software.
;  3. This notice may not be removed or altered from any source distribution.

zx0_decompress:
        movem.l a2/d2,-(sp)  ; preserve registers
        moveq.l #-128,d1        ; initialize empty bit queue
                                ; plus bit to roll into carry
        moveq.l #-1,d2          ; initialize rep-offset to 1
        bra.s   .literals

.do_copy_offs2
        move.b  (a1,d2.l),(a1)+
        move.b  (a1,d2.l),(a1)+

        add.b   d1,d1           ; read 'literal or match' bit
        bcs.s   .get_offset     ; if 0: go copy literals

.literals
        ; read number of literals to copy
        moveq.l #1,d0           ; initialize value to 1
.elias_loop1
        add.b   d1,d1           ; shift bit queue, high bit into carry
        bne.s   .got_bit1       ; queue not empty, bits remain
        move.b  (a0)+,d1        ; read 8 new bits
        addx.b  d1,d1           ; shift bit queue, high bit into carry
                                ; and shift 1 from carry into bit queue

.got_bit1
        bcs.s   .got_elias1     ; done if control bit is 1
        add.b   d1,d1           ; read data bit
        addx.w  d0,d0           ; shift data bit into value in d0
        bra.s   .elias_loop1    ; keep reading

.got_elias1
        subq.w  #1,d0           ; dbf will loop until d0 is -1, not 0
.copy_lits
        move.b  (a0)+,(a1)+     ; copy literal byte
        dbra    d0,.copy_lits   ; loop for all literal bytes

        add.b   d1,d1           ; read 'match or rep-match' bit
        bcs.s   .get_offset     ; if 1: read offset, if 0: rep-match

.rep_match
        ; read match length (starts at 1)
        moveq.l #1,d0           ; initialize value to 1
.elias_loop2
        add.b   d1,d1           ; shift bit queue, high bit into carry
        bne.s   .got_bit2       ; queue not empty, bits remain
        move.b  (a0)+,d1        ; read 8 new bits
        addx.b  d1,d1           ; shift bit queue, high bit into carry
                                ; and shift 1 from carry into bit queue

.got_bit2
        bcs.s   .got_elias2     ; done if control bit is 1
        add.b   d1,d1           ; read data bit
        addx.w  d0,d0           ; shift data bit into value in d0
        bra.s   .elias_loop2    ; keep reading

.got_elias2
        subq.w  #1,d0           ; dbra will loop until d0 is -1, not 0
.do_copy_offs
        move.l  a1,a2           ; calculate backreference address
        add.l   d2,a2           ; (dest + negative match offset)
.copy_match
        move.b  (a2)+,(a1)+     ; copy matched byte
        dbra    d0,.copy_match  ; loop for all matched bytes

        add.b   d1,d1           ; read 'literal or match' bit
        bcc.s   .literals       ; if 0: go copy literals

.get_offset
        moveq.l #-2,d0          ; initialize value to $fe

        ; read high byte of match offset
.elias_loop3
        add.b   d1,d1           ; shift bit queue, high bit into carry
        bne.s   .got_bit3       ; queue not empty, bits remain
        move.b  (a0)+,d1        ; read 8 new bits
        addx.b  d1,d1           ; shift bit queue, high bit into carry
                                ; and shift 1 from carry into bit queue

.got_bit3
        bcs.s   .got_elias3     ; done if control bit is 1
        add.b   d1,d1           ; read data bit
        addx.w  d0,d0           ; shift data bit into value in d0
        bra.s   .elias_loop3    ; keep reading

.got_elias3
        addq.b  #1,d0           ; obtain negative offset high byte
        beq.s   .done           ; exit if EOD marker
        move.b  d0,-(sp)        ; transfer negative high byte to stack
        move.w  (sp)+,d2        ; shift it to make room for low byte

        move.b  (a0)+,d2        ; read low byte of offset + 1 bit of len
        asr.l   #1,d2           ; shift len bit into carry/offset in place
        bcs.s   .do_copy_offs2  ; if len bit is set, no need for more
        moveq.l #1,d0           ; initialize length value to 1
        ; read rest of elias-encoded match length
        add.b   d1,d1           ; read data bit
        addx.w  d0,d0           ; shift data bit into value in d0

.elias_loop4
        add.b   d1,d1           ; shift bit queue, high bit into carry
        bne.s   .got_bit4       ; queue not empty, bits remain
        move.b  (a0)+,d1        ; read 8 new bits
        addx.b  d1,d1           ; shift bit queue, high bit into carry
                                ; and shift 1 from carry into bit queue

.got_bit4
        bcs.s   .do_copy_offs   ; done if control bit is 1
        add.b   d1,d1           ; read data bit
        addx.w  d0,d0           ; shift data bit into value in d0
        bra.s   .elias_loop4    ; keep reading
.done
        movem.l (sp)+,a2/d2  ; restore preserved registers
        rts

        }}
    }

}
