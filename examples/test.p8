%import textio
%import diskio
%import floats
%import graphics
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {
        ubyte value = 100
        ubyte add = 1

        sub dinges() {
            value+=add
        }

        sub dinges2() {
            value+=add
        }

        txt.print_ub(value)
        txt.chrout('\n')
        dinges()
        dinges2()
        txt.print_ub(value)
        txt.chrout('\n')
    }
}
