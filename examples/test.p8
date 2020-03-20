%import c64utils
%import c64flt
%option enable_floats
%zeropage basicsafe

main {

    struct Color {
        uword red
        uword green
        uword blue
    }

    sub start() {
        Color c = {1,2,3}
        Color c2 = {3,4,5}
        c=c2
        c64scr.print_uw(c.red)
        c64.CHROUT('\n')
        c64scr.print_uw(c.green)
        c64.CHROUT('\n')
        c64scr.print_uw(c.blue)
        c64.CHROUT('\n')
        c= {111,222,333}
        c64scr.print_uw(c.red)
        c64.CHROUT('\n')
        c64scr.print_uw(c.green)
        c64.CHROUT('\n')
        c64scr.print_uw(c.blue)
        c64.CHROUT('\n')
    }

 }
