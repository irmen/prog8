%import textio
%zeropage basicsafe
%option no_sysinit
%option enable_floats

main {
    union Together {
        ubyte u
        uword w
        float f
        bool b
    }

    sub start() {
        txt.print_ub(sizeof(Together))          ; 5
        txt.nl()
        txt.print_ub(offsetof(Together.u))      ; 0
        txt.nl()
        txt.print_ub(offsetof(Together.b))      ; 0
        txt.nl()

        ^^Together t = 4000
        t.b = false
        txt.print_bool(t.b)
        txt.spc()
        txt.print_ub(t.u)
        txt.spc()
        t.w=$ea31
        txt.print_bool(t.b)
        txt.spc()
        txt.print_ubhex(t.u, true)
        txt.nl()
    }
}
