%import textio
%import diskio
%import floats
%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {
    sub start () {
        float x = floo()
        floats.print_f(x2)
        test_stack.test2()
        blerp=foobar+xxx
    }

    sub floo() -> float {
        float fl = 1.1
        return flxxx * 2
    }
}
