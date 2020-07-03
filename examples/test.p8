%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats


; TODO: fix register argument clobbering when calling asmsubs.
; for instance if the first arg goes into Y, and the second in A,
; but when calculating the second argument clobbers Y, the first argument gets destroyed.

main {

    sub start() {
        turtle.pu()
        turtle.pu()
        turtle.pu()
        turtle.pu()
        turtle.pu()
        turtle.pu()
        turtle.pu()
        turtle.pu()
        turtle.pu()
        turtle.pu()
    }
}


turtle {
    ubyte pendown

    sub pu() {
        pendown = false
    }

}
