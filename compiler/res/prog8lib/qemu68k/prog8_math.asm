  SECTION .bss,bss

math._sqrt_ub.value:   ds.b 1
math._sqrt_uw.value:   ds.w 1
math._sqrt_l.value:    ds.l 1

  SECTION .text,code  ; back to code section


math._sqrt_ub:
    moveq  #0,d1
    clr.l  d0
    move.b  math._sqrt_ub.value,d0
    beq  .done_ub
    moveq  #4,d2
.loop_ub:
    move.l  d1,d3
    bset  d2,d3
    mulu.w  d3,d3
    cmp.w  d0,d3
    bhi  .skip_ub
    bset  d2,d1
.skip_ub:
    subq  #1,d2
    bpl  .loop_ub
.done_ub:
    move.b  d1,d0
    rts

math._sqrt_uw:
    moveq  #0,d1
    move.w  math._sqrt_uw.value,d0
    beq  .done_uw
    moveq  #8,d2
.loop_uw:
    move.l  d1,d3
    bset  d2,d3
    mulu.w  d3,d3
    cmp.l  d0,d3
    bhi  .skip_uw
    bset  d2,d1
.skip_uw:
    subq  #1,d2
    bpl  .loop_uw
.done_uw:
    move.b  d1,d0
    rts

math._sqrt_l:
    moveq  #0,d1
    move.l  math._sqrt_l.value,d0
    beq  .done_l
    moveq  #15,d2
.loop_l:
    move.l  d1,d3
    bset  d2,d3
    mulu.w  d3,d3
    cmp.l  d0,d3
    bhi  .skip_l
    bset  d2,d1
.skip_l:
    subq  #1,d2
    bpl  .loop_l
.done_l:
    move.w  d1,d0
    rts
