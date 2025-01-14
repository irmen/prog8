%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
;        uword @shared uw1 = 1001
;        uword @shared uw2 = 1001
;        ubyte @shared ub = 42
;        ubyte @shared index = 2
;
;        uword[] array1 = [999,1000,1001]
;        uword[] @nosplit array2 = [999,1000,1001]

;        func2(array1[index], ub)      ; args via subroutine variables  (R1, R2)
;        func2(uw2, ub)                ; args via subroutine variables  (R1, R2)
;        func1(1001)                   ; arg via AY? or R1?
;        func1(array1[index])          ; arg via AY? or R1?
;        func1(array2[index])          ; arg via AY? or R1?

        main.func1.arg1 = 9999
        %asm {{
            lda  #0
            ldy  #0
            jsr  p8s_func1
        }}
        cx16.r3 = 8888
        %asm {{
            lda  #0
            ldy  #0
            jsr  p8s_func1
        }}
    }

;    sub func2(uword arg1 @R1, ubyte arg2 @R2) {   ; expected args via variables  R1+R2
;        txt.print_uw(arg1)
;        txt.chrout('=')
;        txt.print_uw(cx16.r1)
;        txt.spc()
;        txt.print_ub(arg2)
;        txt.chrout('=')
;        txt.print_ub(cx16.r2L)
;        txt.nl()
;    }

    sub func1(uword arg1 @R3) {   ; expected arg1 via R3,  not via cpu regs AY
        txt.print_uw(arg1)
        txt.chrout('=')
        txt.print_uw(cx16.r3)
        txt.nl()
    }
}
