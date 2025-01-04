%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        word @shared dx
        uword @shared udx
        dx++
        udx++
        dx = -5000
        if abs(dx) < 9999
            txt.print("yes1")
        else
            txt.print("no2")

        dx = -15000
        if abs(dx) < 9999
            txt.print("yes2")
        else
            txt.print("no2")
    }
}
