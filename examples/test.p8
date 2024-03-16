%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        txt.print("hello, world!")
        ubyte col = txt.get_column()
        ubyte row = txt.get_row()
        txt.row(10)
        txt.print("row 10")
        txt.column(2)
        txt.print("col 2")
        txt.plot(0, 18)
    }
}


