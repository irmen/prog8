%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        txt.print("\n\n\n\n\n\n                                  !")

        ubyte column, row

        column, row = txt.get_cursor()
        txt.nl()
        txt.print_ub(column)
        txt.spc()
        txt.print_ub(row)
        txt.nl()


        repeat {
        }
    }
}
