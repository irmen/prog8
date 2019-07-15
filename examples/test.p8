%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    struct Color {
        word red
        byte green
        float blue
    }

    sub start() {
        Color rgb1
        Color rgb2

        c64scr.print_b(rgb1.green)
        c64scr.print_b(rgb2.green)
    }

}
