%import c64lib
%import c64utils

main {

    str title="bla"
    struct Color {
        ubyte red
        ubyte green
        ubyte blue
    }

    sub start() {
        str subtitle = "basdf"
        Color rgb

        derp.dop()

        uword zz = &title
        zz=&main.title
        zz=&subtitle
        zz=&main.start.subtitle

;        uword addr = &derp.dop.name         ; @todo strange error "pointer-of operand must be the name of a heap variable"
;        c64scr.print(&derp.dop.name)

        zz=&rgb
    }
}


derp {

    sub dop() {
        ubyte zzz=33
        str name="irmen"

        A=54
    }
}
