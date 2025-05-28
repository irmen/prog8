%import textio
%import floats

main {
    ^^byte @shared sbptr
    ^^ubyte @shared ubptr
    ^^bool @shared bptr
    ^^word @shared wptr
    ^^float @shared fptr

    struct List {
        ubyte value
        ^^List next
    }

    sub start() {
        ^^List l1 = 1000
        ^^List l2 = 1100
        ^^List l3 = 1200
        ^^List l4 = 1300

        l1.next = l2
        l1.value = 101
        l2.next = l3
        l2.value = 102
        l3.next = l4
        l3.value = 103
        l4.next = 0
        l4.value = 104

        txt.print_uw(l1.next)
        txt.spc()
        txt.print_uw(l2.next)
        txt.spc()
        txt.print_uw(l3.next)
        txt.spc()
        txt.print_uw(l4.next)
        txt.nl()

        txt.print_ub(l1.value)
        txt.spc()
        txt.print_ub(l2.value)
        txt.spc()
        txt.print_ub(l3.value)
        txt.spc()
        txt.print_ub(l4.value)
        txt.nl()

        txt.print_ub(l1.next.next.next.value)
        txt.nl()
        l1.next.next.next.value = 99
        txt.print_ub(l4.value)
        txt.nl()

        bool @shared zz
        byte sb
        ubyte ub
        float f
        word w

        ubptr = 2300
        bptr = 2000
        sbptr = 2200
        fptr = 3000
        wptr = 2100
        poke(2300, 199)
        poke(2000, 1)
        poke(2200, -111 as ubyte)
        pokew(2100, 42080)
        pokef(3000, 3.1415)

        txt.print(" correct values: ")
        txt.print_ub(peek(2300))
        txt.spc()
        txt.print_b(peek(2200) as byte)
        txt.spc()
        txt.print_bool(peek(2000))
        txt.spc()
        txt.print_w(peekw(2100) as word)
        txt.spc()
        txt.print_f(peekf(3000))
        txt.nl()

        assignptrderef()
        txt.print("    test values: ")
        txt.print_ub(ub)
        txt.spc()
        txt.print_b(sb)
        txt.spc()
        txt.print_bool(zz)
        txt.spc()
        txt.print_w(w)
        txt.spc()
        txt.print_f(f)
        txt.nl()

        assignptrderef()
        txt.print("2nd test values: ")
        txt.print_ub(ub)
        txt.spc()
        txt.print_b(sb)
        txt.spc()
        txt.print_bool(zz)
        txt.spc()
        txt.print_w(w)
        txt.spc()
        txt.print_f(f)
        txt.nl()

        sub assignptrderef() {
            ub = ubptr^^
            sb = sbptr^^
            zz = bptr^^
            f = fptr^^
            w = wptr^^

            ubptr^^ = 99; ub
            sbptr^^ = 55; sb
            bptr^^ = false; zz
            wptr^^ = 7777; w
            fptr^^ = 9.999 ;f
        }

    }
}

