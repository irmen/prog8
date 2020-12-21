%import textio
%import diskio
%import floats
%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {
    sub start () {
        float fl

        fl = log2(10)
        floats.print_f(fl)
    }
}
