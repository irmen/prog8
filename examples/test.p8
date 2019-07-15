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
        Color rgb1 = {1,2,3.44}
        Color rgb2

        rgb2 = {22233, 33, 1.1}     ; @todo implicit type conversion
        c64scr.print_b(rgb1.green)
        c64.CHROUT('\n')
        c64scr.print_b(rgb2.green)
        c64.CHROUT('\n')

        rgb1=rgb2
        c64scr.print_b(rgb1.green)
        c64.CHROUT('\n')
    }

}
