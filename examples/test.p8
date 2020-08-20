%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        struct Color {
            ubyte red
            uword green
            float blue
        }

        Color c = [11,22222,3.1234]

        str string = "irmen"
        byte[]  ab = [1,2,3]
        ubyte[]  aub = [1,2,3]
        word[]  aw = [11,22,33]
        uword[]  auw = [11,22,33]
        float[]  af = [1.1,2.2,3.3]

        c64scr.print_ub(sizeof(ab))
        c64.CHROUT('\n')
        c64scr.print_ub(sizeof(aub))
        c64.CHROUT('\n')
        c64scr.print_ub(sizeof(aw))
        c64.CHROUT('\n')
        c64scr.print_ub(sizeof(auw))
        c64.CHROUT('\n')
        c64scr.print_ub(sizeof(af))
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        c64scr.print_ub(c.red)
        c64.CHROUT('\n')
        c64scr.print_uw(c.green)
        c64.CHROUT('\n')
        c64flt.print_f(c.blue)
        c64.CHROUT('\n')

        ubyte size = sizeof(Color)
        c64scr.print_ub(size)
        c64.CHROUT('\n')
        c64scr.print_ub(sizeof(Color))
        c64.CHROUT('\n')
        c64scr.print_ub(sizeof(c))
        c64.CHROUT('\n')
        c64scr.print_ub(sizeof(c.red))
        c64.CHROUT('\n')
        c64scr.print_ub(sizeof(c.green))
        c64.CHROUT('\n')
        c64scr.print_ub(sizeof(c.blue))
        c64.CHROUT('\n')
    }
}
