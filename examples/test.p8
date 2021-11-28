%import textio
%import string
%import test_stack

main {

    ubyte[23]  savedata
    ubyte[17] cargohold = 0
    uword filenameptr = $c000

    sub start() {
        test_stack.test()

        uword @shared uw
        ubyte @shared ub
        word @shared ww
        byte @shared bb

        push(-bb)
        pop(ub)
        txt.print_ub(ub)
        txt.nl()
        pushw(32767)
        popw(uw)
        txt.print_uw(uw)
        txt.nl()

;        uw=10000
;        routines(44,uw+123)
;        routines2(44,uw+123)
;
;        routine(uw+123, 22,33, true, 44)
;        routine2(uw+123, 22,33, true, 44)

        test_stack.test()

        repeat {
        }

    }

    sub routine(uword num, ubyte a1, ubyte a2, ubyte switch, byte a3) {
        txt.print_uw(num)
        txt.spc()
        txt.print_ub(a1)
        txt.spc()
        txt.print_ub(a2)
        txt.spc()
        txt.print_ub(switch)
        txt.spc()
        txt.print_b(a3)
        txt.nl()
    }

    sub routines(ubyte bb, uword num) {
        txt.print_ub(bb)
        txt.spc()
        txt.print_uw(num)
        txt.nl()
    }

    asmsub routine2(uword num @AY, ubyte a1 @R1, ubyte a2 @R0, ubyte switch @Pc, ubyte a3 @X) {
        %asm {{
            sta  routine.num
            sty  routine.num+1
            lda  #0
            adc  #0
            sta  routine.switch
            lda  cx16.r0
            sta  routine.a2
            lda  cx16.r1
            sta  routine.a1
            stx  routine.a3
            jmp  routine
        }}
    }

    asmsub routines2(ubyte bb @X, uword num @AY) {
        %asm {{
            sta  routines.num
            sty  routines.num+1
            stx  routines.bb
            jmp  routines
        }}
    }

}
