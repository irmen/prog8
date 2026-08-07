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

  SECTION .text,code

; d0 = d0 * d1  (signed or unsigned, keeping the low 32 bits)
; Uses four 16-bit partial products:  result = x0*y0 + (x0*y1 + x1*y0)<<16  (+ x1*y1<<32, dropped).
p8_mulsi3:
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
    rts

; Unsigned 32/32 -> 32: d0 = dividend, d1 = divisor  ->  d0 = quotient, d1 = remainder.
; Plain shift-and-subtract long division, 32 iterations, no 32-bit divide needed.
p8_udivmod32_sw:
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
    rts
.divz:
    move.l  d0,d1          ; remainder = dividend
    clr.l   d0             ; quotient = 0
    rts

; Signed 32/32 -> 32: d0 = dividend, d1 = divisor  ->  d0 = quotient, d1 = remainder.
; Computed with absolute values, then the signs are corrected: the quotient takes
; the sign of (dividend xor divisor), the remainder takes the sign of the dividend
; (truncating division, like C).
p8_sdivmod32_sw:
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
    rts

; Wrappers. d0/d1 in, d0 (and d1 for divmod) out. When sys.UtilityBase is non-zero
; (Kickstart 2.0+) the amiga utility.library is used for a fast result; otherwise the
; software routines above are used.

; d0*d1 (signed) -> d0
p8_smult32:
    move.l  sys.UtilityBase,d2   ; scratch (d0=arg1, d1=arg2 preserved)
    beq.w   p8_mulsi3
    move.l  d2,a6
    jmp     -138(a6)      ; utility.library SMult32

; d0/d1 (signed) -> d0=quotient, d1=remainder
p8_sdivmod32:
    move.l  sys.UtilityBase,d2   ; scratch (d0=dividend, d1=divisor preserved)
    beq.w   p8_sdivmod32_sw
    move.l  d2,a6
    jmp     -150(a6)      ; utility.library SDivMod32
