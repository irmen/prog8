; 68000 (amiga500) 32-bit multiplication / division / modulo
;
; The plain 68000 only has 8x8 and 16x16 multiply/divide instructions, so the
; 32-bit operations have to be built up from those. The routines below are
; written to integrate directly with the code generator: they take their
; arguments in registers (d0/d1) and return their results in registers, and the
; division/modulo share a single long-division core that produces both quotient
; and remainder at once.
;
; The p8_*32 wrapper routines use the amiga utility.library for a fast path on
; Kickstart 2.0+ (when sys.UtilityBase is non-zero), with a software fallback
; for Kickstart 1.3 (where utility.library is not available).
;
; The software routines preserve the callee-saved registers they use. The wrappers
; call utility.library, which is not assumed to preserve D2-D7 (D6 is volatile across
; AmigaOS library calls), so generated code saves D2-D7 around every call to them.
; See ideas/m68k-register-allocation.md section 2.6.

  SECTION .text,code

; d0 = d0 * d1  (signed or unsigned, keeping the low 32 bits)
; Uses four 16-bit partial products:  result = x0*y0 + (x0*y1 + x1*y0)<<16  (+ x1*y1<<32, dropped).
p8_mulsi3:
    movem.l d2-d3,-(sp)
    move.w  d0,d2          ; d2 = x0 (low half of x)
    mulu.w  d1,d2          ; d2 = x0*y0
    move.l  d0,d3
    lsr.l   #8,d3
    lsr.l   #8,d3          ; d3 = x1 (high half of x)
    mulu.w  d1,d3          ; d3 = x1*y0
    swap    d1             ; d1 = y1 (high half of y)
    mulu.w  d0,d1          ; d1 = x0*y1
    add.l   d3,d1          ; d1 = x0*y1 + x1*y0  (the two cross terms)
    swap    d1
    clr.w   d1             ; d1 = (cross terms) << 16
    add.l   d1,d2          ; d2 = x0*y0 + cross<<16
    move.l  d2,d0
    movem.l (sp)+,d2-d3
    rts

; Unsigned 32/32 -> 32: d0 = dividend, d1 = divisor  ->  d0 = quotient, d1 = remainder.
; Plain shift-and-subtract long division, 32 iterations, no 32-bit divide needed.
p8_udivmod32_sw:
    movem.l d2-d5,-(sp)
    move.l  d1,d2          ; d2 = divisor
    beq.s   .divz          ; divide by zero: quotient 0, remainder = dividend
    moveq   #31,d3         ; 32 bit positions to process
    move.l  d0,d4          ; d4 = dividend (shifted through to feed the bits)
    clr.l   d0             ; d0 = quotient (built up)
    clr.l   d5             ; d5 = remainder (built up)
.loop:
    lsl.l   #1,d4          ; shift the next dividend bit out into the X flag
    roxl.l  #1,d5          ; remainder = (remainder<<1) | that dividend bit
    lsl.l   #1,d0          ; quotient <<= 1
    cmp.l   d2,d5
    blo.s   .next
    sub.l   d2,d5          ; remainder -= divisor
    addq.l  #1,d0          ; quotient |= 1
.next:
    dbf     d3,.loop
    move.l  d5,d1          ; remainder
.out:
    movem.l (sp)+,d2-d5
    rts
.divz:
    move.l  d0,d1          ; remainder = dividend
    clr.l   d0             ; quotient = 0
    bra.s   .out

; Signed 32/32 -> 32: d0 = dividend, d1 = divisor  ->  d0 = quotient, d1 = remainder.
; Computed with absolute values, then the signs are corrected: the quotient takes
; the sign of (dividend xor divisor), the remainder takes the sign of the dividend
; (truncating division, like C).
p8_sdivmod32_sw:
    movem.l d6-d7,-(sp)
    move.l  d0,d6          ; d6 = dividend (keep original for sign)
    move.l  d1,d7
    eor.l   d6,d7          ; d7 bit31 set if the two signs differ
    tst.l   d0
    bpl.s   .nabs
    neg.l   d0             ; d0 = |dividend|
.nabs:
    tst.l   d1
    bpl.s   .dabs
    neg.l   d1             ; d1 = |divisor|
.dabs:
    jsr     p8_udivmod32_sw  ; d0 = |quotient|, d1 = |remainder|
    tst.l   d7
    bpl.s   .qpos
    neg.l   d0             ; flip quotient sign if signs differed
.qpos:
    tst.l   d6
    bpl.s   .rpos
    neg.l   d1             ; flip remainder sign to match dividend
.rpos:
    movem.l (sp)+,d6-d7
    rts

; Wrappers. d0/d1 in, d0 (and d1 for divmod) out. When sys.UtilityBase is non-zero
; (Kickstart 2.0+) the amiga utility.library is used for a fast result; otherwise the
; software routines above are used. Offsets checked against NDK 3.2 FD/utility_lib.fd.

; d0*d1 -> d0 (signed)
p8_smult32:
    tst.l   sys.UtilityBase
    beq.s   .mulsw
    move.l  sys.UtilityBase,a6
    jmp     -138(a6)      ; utility.library SMult32
.mulsw:
    jmp     p8_mulsi3

; d0*d1 -> d0 (unsigned)
p8_umult32:
    tst.l   sys.UtilityBase
    beq.s   .mulsw
    move.l  sys.UtilityBase,a6
    jmp     -144(a6)      ; utility.library UMult32
.mulsw:
    jmp     p8_mulsi3

; d0/d1 (signed) -> d0=quotient, d1=remainder
p8_sdivmod32:
    tst.l   sys.UtilityBase
    beq.s   .divsw
    move.l  sys.UtilityBase,a6
    jmp     -150(a6)      ; utility.library SDivMod32
.divsw:
    jmp     p8_sdivmod32_sw

; d0/d1 (unsigned) -> d0=quotient, d1=remainder
p8_udivmod32:
    tst.l   sys.UtilityBase
    beq.s   .divsw
    move.l  sys.UtilityBase,a6
    jmp     -156(a6)      ; utility.library UDivMod32
.divsw:
    jmp     p8_udivmod32_sw
