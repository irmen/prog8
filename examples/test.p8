%import c64utils
%zeropage basicsafe

~ main {

    sub start() {
        str  naam = "                               "

        while true {
            c64scr.print("naam: ")
            ubyte length = c64scr.input_chars(naam)
            c64scr.print_ub(length)
            c64.CHROUT(':')
            c64scr.print(naam)
            c64.CHROUT('\n')
        }
    }

}
