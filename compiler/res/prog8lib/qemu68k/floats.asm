; Floating point routines for m68k target (using 68881/68882 FPU, requires 68020+ CPU)


  SECTION .text,code

; floats.tostr implementation
; Input: FP0 = float value
; Output: A0 = pointer to null-terminated ASCII string (in floats.str_buf)
; Clobbers: D0-D5, A0-A1, FP0-FP1
floats._tostr:
  lea  floats.str_buf,a1        ; A1 = output cursor

  ; == Check for zero ==
  ftst.x  fp0
  fbne  .not_zero

  move.b  #'0',(a1)+
  move.b  #'.',(a1)+
  move.b  #'0',(a1)+
  clr.b  (a1)
  lea  floats.str_buf,a0
  rts

.not_zero:
  ; == Check sign, infinity, NaN from raw bits ==
  fmove.s  fp0,floats.str_buf    ; reuse str_buf start as temp (before we write output)
  move.l  floats.str_buf,d0
  move.l  d0,d3
  and.l  #$7fffffff,d0

  ; == Check for infinity ==
  cmp.l  #$7f800000,d0
  beq  .infinity
  bhi  .nan

  ; == Handle sign ==
  tst.l  d3
  bpl  .get_whole_part
  move.b  #'-',(a1)+
  fabs.x  fp0

.get_whole_part:
  ; == Extract integer part ==
  fintrz.x  fp0,fp1
  fmove.l  fp1,d0                ; D0 = integer part

  ; == Convert integer part to ASCII using stack as digit buffer ==
  moveq   #0,d5                  ; digit counter

.div_loop:
  clr.l  d1                      ; clear upper 32 bits of 64-bit dividend
  divu.l  #10,d1:d0              ; D0=quotient, D1=remainder
  move.l  d1,-(sp)               ; push digit onto stack
  addq.l  #1,d5                  ; increment digit count
  tst.l  d0
  bne  .div_loop

  tst.l  d5
  bne  .pop_int_digits
  move.b  #'0',(a1)+             ; integer part was zero

.pop_int_digits:
  move.l  (sp)+,d1               ; pop digit from stack
  add.b  #'0',d1
  move.b  d1,(a1)+               ; write to output buffer
  subq.l  #1,d5
  bne  .pop_int_digits

  ; == Decimal point ==
  move.b  #'.',(a1)+

  ; == Extract fractional part ==
  fsub.x  fp1,fp0

  moveq  #5,d4                   ; 6 digits

.frac_loop:
  fmul.w  #10,fp0
  fintrz.x  fp0,fp1
  fmove.l  fp1,d2
  add.b  #'0',d2
  move.b  d2,(a1)+
  fsub.x  fp1,fp0
  dbra  d4,.frac_loop

  ; == Null-terminate ==
  clr.b  (a1)
  lea  floats.str_buf,a0
  rts

.infinity:
  tst.l  d3
  bpl  .inf_pos
  move.b  #'-',(a1)+
.inf_pos:
  move.l  #$496e6600,(a1)+       ; "Inf\0"
  lea  floats.str_buf,a0
  rts

.nan:
  move.l  #$4e614e00,(a1)+       ; "NaN\0"
  lea  floats.str_buf,a0
  rts


  SECTION .bss,bss

floats.str_buf:
  ds.b 32

  SECTION .text,code  ; back to code section for remainder of assembly
