; CommanderX16 floating point example!
; make sure to compile with the cx16 compiler target.

%import cx16textio
%import cx16flt
%zeropage basicsafe

main {

    sub start() {
        float f1 = 5.55
        float f2 = 33.3
        float f3 = f1 * f2

        c64flt.print_f(f1)
        c64.CHROUT('*')
        c64flt.print_f(f2)
        c64.CHROUT('=')
        c64flt.print_f(f3)
        c64.CHROUT('\n')
    }
}

