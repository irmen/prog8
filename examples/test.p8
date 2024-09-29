%import textio
%import string
%import compression
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        test_stack.test()

        txt.print_uwhex(cbm.CHROUT, true)
        txt.print_uwhex(&cbm.CHROUT, true)
        txt.nl()

        cx16.r0 = &function1
        callfar(0, $ffd2, $0031)
        callfar(0, cbm.CHROUT, $000d)
        callfar(0, function1, $6660)
        callfar(0, cx16.r0, $ffff)
        cx16.r0 -=10
        callfar(0, cx16.r0+10, $eeee)

        cx16.r0 = &function2
        callfar(0, $ffd2, $0032)
        callfar(0, cbm.CHROUT, $000d)
        callfar(0, function2, $6660)
        callfar(0, cx16.r0, $ffff)
        cx16.r0 -=10
        callfar(0, cx16.r0+10, $eeee)

        test_stack.test()
        cx16.r0++

        sub function1(uword arg) {
            txt.print("function 1 arg=")
            txt.print_uwhex(arg, false)
            txt.nl()
        }

        sub function2(uword arg) {
            txt.print("function 2 arg=")
            txt.print_uwhex(arg, false)
            txt.nl()
        }
    }
}

