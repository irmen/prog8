%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        corner(100, 200)
    }


    sub corner(ubyte col_c, ubyte row_c) {
        uword col
        uword row

        col = col_c
        col *= 8
        row = row_c
        row *= 8
        ; NOTE: the above two multiplicastions should NOT be optimized to:
        ; col = col_c *8
        ; row = row_c *8
        ; .. because that wil do byte size multiplication only on the RHS!


        txt.print_uw(col)
        txt.spc()
        txt.print_uw(row)
        txt.nl()
    }
}
