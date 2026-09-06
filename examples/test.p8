main {

    sub start() {
        uword @shared sharedvar = 42
        sharedvar = func(sharedvar + 99) + func(4444)
    }

    sub func(uword value) -> uword {
        value += 7
        return value*8
    }
}

/*

The above code for the amiga500 target currently compiles to:


p8b_main.p8s_start:
    bsr  run_global_inits
        ; storeim.w #$2a,p8b_main.p8s_start.p8v_sharedvar
    move.w  #42,p8b_main.p8s_start.p8v_sharedvar
        ; loadm.w r1,p8b_main.p8s_start.p8v_sharedvar
    move.w  p8b_main.p8s_start.p8v_sharedvar,p8_regfile+0
        ; add.w r1,#$63
    add.w  #99,p8_regfile+0
        ; call p8b_main.p8s_func(p8v_value=r1.w):r2.w
    move.w  p8_regfile+0,p8b_main.p8s_func.p8v_value
    bsr  p8b_main.p8s_func
    move.w  d0,p8_regfile+2
        ; load.w r3,#$115c
    move.w  #4444,p8_regfile+4
        ; call p8b_main.p8s_func(p8v_value=r3.w):r4.w
    move.w  p8_regfile+4,p8b_main.p8s_func.p8v_value
    bsr  p8b_main.p8s_func
    move.w  d0,p8_regfile+6
        ; addr.w r2,r4
    add.w  d0,p8_regfile+2
        ; storem.w r2,p8b_main.p8s_start.p8v_sharedvar
    move.w  p8_regfile+2,p8b_main.p8s_start.p8v_sharedvar
        ; return
    rts
; End of subroutine: p8b_main.p8s_start

p8b_main.p8s_func:
        ; addim.w #7,p8b_main.p8s_func.p8v_value
    addq.w  #7,p8b_main.p8s_func.p8v_value
        ; loadm.w r5,p8b_main.p8s_func.p8v_value
    move.w  p8b_main.p8s_func.p8v_value,p8_regfile+8
        ; lsli.w r5,#3
    move.w  p8_regfile+8,d0
    lsl.w  #3,d0
    move.w  d0,p8_regfile+8
        ; returnr.w r5
    rts
; End of subroutine: p8b_main.p8s_func



After the production register allocation, can we expect it to become something like:

p8b_main.p8s_start:
    bsr  run_global_inits
    move.w  #42,p8b_main.p8s_start.p8v_sharedvar
    move.w  p8b_main.p8s_start.p8v_sharedvar,d0
    add.w  #99,d0
    move.w  d0,p8b_main.p8s_func.p8v_value
    bsr  p8b_main.p8s_func
    move.w  d0,d1       ; or maybe   move.w d0,-(sp)
    move.w  #4444,p8b_main.p8s_func.p8v_value
    bsr  p8b_main.p8s_func
    add.w   d0,d1
    move.w  d1,p8b_main.p8s_start.p8v_sharedvar
    rts
; End of subroutine: p8b_main.p8s_start

p8b_main.p8s_func:
    addq.w  #7,p8b_main.p8s_func.p8v_value
    move.w  p8b_main.p8s_func.p8v_value,d0
    lsl.w  #3,d0
    rts  ; directly return value that is already in d0
; End of subroutine: p8b_main.p8s_func



*/
