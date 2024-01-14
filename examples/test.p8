%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        float[] fa = [1.1, 2.2, 3.3]
        float @shared fl = 2.2

        txt.print_ub(fl in fa)
    }
}
