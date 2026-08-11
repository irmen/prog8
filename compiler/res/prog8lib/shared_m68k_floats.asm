; Floating point formatting for the Amiga M68K target.

    section .text,code

floats._tostr:
    lea floats.str_buf,a1
    ftst.x fp0
    fbne .not_zero

    move.b #'0',(a1)+
    move.b #'.',(a1)+
    move.b #'0',(a1)+
    clr.b (a1)
    lea floats.str_buf,a0
    rts

.not_zero:
    fmove.s fp0,floats.str_buf
    move.l floats.str_buf,d0
    move.l d0,d3
    and.l #$7fffffff,d0

    cmp.l #$7f800000,d0
    beq .infinity
    bhi .nan

    tst.l d3
    bpl .get_whole_part
    move.b #'-',(a1)+
    fabs.x fp0

.get_whole_part:
    fintrz.x fp0,fp1
    fmove.l fp1,d0
    moveq #0,d5

.div_loop:
    clr.l d1
    divu.l #10,d1:d0
    move.l d1,-(sp)
    addq.l #1,d5
    tst.l d0
    bne .div_loop

    tst.l d5
    bne .pop_int_digits
    move.b #'0',(a1)+

.pop_int_digits:
    move.l (sp)+,d1
    add.b #'0',d1
    move.b d1,(a1)+
    subq.l #1,d5
    bne .pop_int_digits

    move.b #'.',(a1)+
    fsub.x fp1,fp0
    moveq #5,d4

.frac_loop:
    fmul.w #10,fp0
    fintrz.x fp0,fp1
    fmove.l fp1,d2
    add.b #'0',d2
    move.b d2,(a1)+
    fsub.x fp1,fp0
    dbra d4,.frac_loop

    clr.b (a1)
    lea floats.str_buf,a0
    rts

.infinity:
    tst.l d3
    bpl .inf_pos
    move.b #'-',(a1)+
.inf_pos:
    move.l #$496e6600,(a1)+
    lea floats.str_buf,a0
    rts

.nan:
    move.l #$4e614e00,(a1)+
    lea floats.str_buf,a0
    rts

    section .bss,bss
floats.str_buf:
    ds.b 32

    section .data,data
floats._fzero:
    dc.s 0.0
floats._fone:
    dc.s 1.0
floats._fneg_one:
    dc.s -1.0
floats._ften:
    dc.s 10.0
floats._rnd_scale:
    dc.s 16777216.0

    section .bss,bss
floats._rnd_state:
    ds.l 1

    section .text,code
