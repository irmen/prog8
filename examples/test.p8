;%import c64lib
;%import c64graphics
;%import c64textio
;%import c64flt
;%option enable_floats
%target cx16
%import cx16textio
%zeropage basicsafe


main {

    sub start()  {

        txt.lowercase()
        txt.print("Hello there")
        uword ww
        ubyte bb=44
        ww = bb*($0032)
        txt.print_uw(ww)

        txt.setchr(5, 5, '*')
        txt.setchr(6, 5, '*')
        txt.setchr(7, 5, '*')
        txt.setchr(7, 6, '+')
        txt.setchr(7, 7, '+')

        txt.setclr(5, 5, 1)
        txt.setclr(6, 5, 2)
        txt.setclr(7, 5, 3)
        txt.setclr(7, 6, 4)
        txt.setclr(7, 7, 5)

        txt.plot(15,10)
        txt.chrout('!')

        txt.print_ub(txt.getchr(4,5))
        txt.chrout(',')
        txt.print_ub(txt.getchr(5,5))
        txt.chrout(',')
        txt.print_ub(txt.getchr(6,5))
        txt.chrout(',')
        txt.print_ub(txt.getchr(7,5))
        txt.chrout(',')
        txt.print_ub(txt.getchr(8,5))
        txt.chrout('\n')
        txt.print_ub(txt.getclr(4,5))
        txt.chrout(',')
        txt.print_ub(txt.getclr(5,5))
        txt.chrout(',')
        txt.print_ub(txt.getclr(6,5))
        txt.chrout(',')
        txt.print_ub(txt.getclr(7,5))
        txt.chrout(',')
        txt.print_ub(txt.getclr(8,5))
        txt.chrout('\n')

        txt.print("width:")
        txt.print_ub(txt.width())
        txt.print(" height:")
        txt.print_ub(txt.height())

    }
}
