;%import test_stack
;%import textio
%import gfx2
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {
;        txt.lowercase()
;        txt.print_ub(txt.width())
;        txt.chrout('\n')
;        txt.print_ub(txt.height())
;        txt.chrout('\n')
        gfx2.text(0,0,2, "sdafsdf")
;        test_stack.test()
    }

}
