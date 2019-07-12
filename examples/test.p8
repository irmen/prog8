%import c64utils
%zeropage basicsafe

~ main {

    struct Color {
        uword red
        ubyte green
        ubyte blue
    }

    sub start() {

        Color col_one
        Color col_two
        Color col_three

        col_one.red= 111
        col_two.blue= 222

        c64scr.print_uwhex(1, &col_one)
        c64scr.print_uwhex(1, &col_two)
        c64scr.print_uwhex(1, &col_three)

        return
    }

}
