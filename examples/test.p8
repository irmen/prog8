;%import c64lib
;%import c64graphics
;%import c64textio
%import c64flt
;%option enable_floats
%target cx16
%import cx16textio
%zeropage basicsafe


main {

    sub start()  {

        const ubyte cbvalue = 40
        const uword cwvalue = cbvalue
        uword wvalue = 40

        ubyte x
        ubyte bb = 99
        x = msb(sin8u(bb) * cwvalue)
        txt.print_ub(x)
        txt.chrout('\n')
        x = msb(sin8u(bb) * wvalue)
        txt.print_ub(x)
        txt.chrout('\n')
        txt.chrout('\n')

        x = msb(cwvalue*sin8u(bb))
        txt.print_ub(x)
        txt.chrout('\n')
        x = msb(wvalue*sin8u(bb))
        txt.print_ub(x)
        txt.chrout('\n')
    }
}
