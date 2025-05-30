%import textio
%zeropage basicsafe

main {
    sub start() {
        bytes()
        words()
    }

    sub bytes() {
        byte @shared sb = -100
        byte @shared p = 100
        const byte cb = -100

        txt.print("\nsigned bytes\n")
        txt.print("expected: ")
        txt.print_b(cb)
        txt.spc()
        txt.print_bool(cb>100)
        txt.spc()
        txt.print_bool(cb>=100)
        txt.spc()
        txt.print_bool(cb<100)
        txt.spc()
        txt.print_bool(cb<=100)
        txt.nl()

        txt.print("  calc'd: ")
        txt.print_b(sb)
        txt.spc()
        txt.print_bool(sb>100)
        txt.spc()
        txt.print_bool(sb>=100)
        txt.spc()
        txt.print_bool(sb<100)
        txt.spc()
        txt.print_bool(sb<=100)
        txt.nl()

        txt.print("calc'd 2: ")
        txt.print_b(sb)
        txt.spc()
        txt.print_bool(sb>p)
        txt.spc()
        txt.print_bool(sb>=p)
        txt.spc()
        txt.print_bool(sb<p)
        txt.spc()
        txt.print_bool(sb<=p)
        txt.nl()

        txt.print(" if stmt: ")
        txt.print_b(sb)
        txt.spc()
        if sb>100 txt.print("true ") else txt.print("false ")
        if sb>=100 txt.print("true ") else txt.print("false ")
        if sb<100 txt.print("true ") else txt.print("false ")
        if sb<=100 txt.print("true ") else txt.print("false ")
        txt.nl()

        txt.print(" ifstmt2: ")
        txt.print_b(sb)
        txt.spc()
        if sb>p txt.print("true ") else txt.print("false ")
        if sb>=p txt.print("true ") else txt.print("false ")
        if sb<p txt.print("true ") else txt.print("false ")
        if sb<=p txt.print("true ") else txt.print("false ")
        txt.nl()
    }

    sub words() {
        word @shared sbw = -30000
        word @shared pw = 30000
        const word cbw = -30000

        txt.print("\nsigned words\n")
        txt.print("expected: ")
        txt.print_w(cbw)
        txt.spc()
        txt.print_bool(cbw>30000)
        txt.spc()
        txt.print_bool(cbw>=30000)
        txt.spc()
        txt.print_bool(cbw<30000)
        txt.spc()
        txt.print_bool(cbw<=30000)
        txt.nl()

        txt.print("  calc'd: ")
        txt.print_w(sbw)
        txt.spc()
        txt.print_bool(sbw>30000)
        txt.spc()
        txt.print_bool(sbw>=30000)
        txt.spc()
        txt.print_bool(sbw<30000)
        txt.spc()
        txt.print_bool(sbw<=30000)
        txt.nl()

        txt.print("calc'd 2: ")
        txt.print_w(sbw)
        txt.spc()
        txt.print_bool(sbw>pw)
        txt.spc()
        txt.print_bool(sbw>=pw)
        txt.spc()
        txt.print_bool(sbw<pw)
        txt.spc()
        txt.print_bool(sbw<=pw)
        txt.nl()

        txt.print(" if stmt: ")
        txt.print_w(sbw)
        txt.spc()
        if sbw>30000 txt.print("true ") else txt.print("false ")
        if sbw>=30000 txt.print("true ") else txt.print("false ")
        if sbw<30000 txt.print("true ") else txt.print("false ")
        if sbw<=30000 txt.print("true ") else txt.print("false ")
        txt.nl()

        txt.print(" ifstmt2: ")
        txt.print_w(sbw)
        txt.spc()
        if sbw>pw txt.print("true ") else txt.print("false ")
        if sbw>=pw txt.print("true ") else txt.print("false ")
        if sbw<pw txt.print("true ") else txt.print("false ")
        if sbw<=pw txt.print("true ") else txt.print("false ")
        txt.nl()
    }
}
