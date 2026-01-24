%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.home()
        repeat 10 txt.nl()
        repeat 20 txt.spc()
        txt.print("hello")
        ubyte row, column
        ubyte row2, column2
        column, row = txt.get_cursor()
        column2 = txt.get_column()
        row2 = txt.get_row()
        txt.nl()
        txt.print_ub(column)
        txt.spc()
        txt.print_ub(column2)
        txt.nl()
        txt.print_ub(row)
        txt.spc()
        txt.print_ub(row2)
        txt.nl()
        txt.column(6)
        txt.print("world")
        txt.column(37)
        txt.print("yep")
        txt.nl()
        for cx16.r0L in 4 to 8 {
            txt.column(cx16.r0L+10)
            txt.row(cx16.r0L)
            txt.chrout('*')
        }
        txt.print("done")
        txt.home()
    }
}
