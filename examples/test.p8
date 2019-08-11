%import c64lib
%import c64utils

main {

    sub start() {
        derp.dop()

        A=derp.dop.zzz

        derp.dop.zzz=3


        uword addr = &derp.dop.name         ; @todo strange error "pointer-of operand must be the name of a heap variable"
        c64scr.print(&derp.dop.name)
    }
}


derp {

    sub dop() {
        ubyte zzz=33
        str name="irmen"

        A=54
    }
}
