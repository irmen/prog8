%import textio
%import verafx
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword result, resulthi
        result, resulthi = verafx.mult(9344, 6522)
        txt.print_uwhex(resulthi, true)
        txt.spc()
        txt.print_uwhex(result, false)
        txt.nl()

        word sresult, sresulthi
        sresult, sresulthi = verafx.muls(9344, -6522)
        txt.print_w(sresulthi)
        txt.spc()
        txt.print_w(sresult)
        txt.nl()

        sresult, sresulthi = verafx.muls(144, -22)
        txt.print_w(sresulthi)
        txt.spc()
        txt.print_w(sresult)
        txt.nl()
    }
}
