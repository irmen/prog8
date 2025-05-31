%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.plot(0, 49)
        bytesoverflow()
        bytesoverflow_jump()
        bytesoverflow_jump_indirect()
        bytessmall()
        bytessmall_jump()
        bytessmall_jump_indirect()
        bytes99()
        bytes100()
        bytes101()
        words()
        zerobytes()
        zerowords()
    }

    sub zerobytes() {
        byte @shared sb = -100
        byte @shared p = 0
        const byte cb = -100

        txt.print("\nsigned bytes with 0\n")
        txt.print("expected: ")
        txt.print_b(cb)
        txt.spc()
        txt.print_bool(cb>0)
        txt.spc()
        txt.print_bool(cb>=0)
        txt.spc()
        txt.print_bool(cb<0)
        txt.spc()
        txt.print_bool(cb<=0)
        txt.nl()

        txt.print("  calc'd: ")
        txt.print_b(sb)
        txt.spc()
        txt.print_bool(sb>0)
        txt.spc()
        txt.print_bool(sb>=0)
        txt.spc()
        txt.print_bool(sb<0)
        txt.spc()
        txt.print_bool(sb<=0)
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
        if sb>0 txt.print("true ") else txt.print("false ")
        if sb>=0 txt.print("true ") else txt.print("false ")
        if sb<0 txt.print("true ") else txt.print("false ")
        if sb<=0 txt.print("true ") else txt.print("false ")
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

    sub zerowords() {
        word @shared sbw = -30000
        word @shared pw = 0
        const word cbw = -30000

        txt.print("\nsigned words\n")
        txt.print("expected: ")
        txt.print_w(cbw)
        txt.spc()
        txt.print_bool(cbw>0)
        txt.spc()
        txt.print_bool(cbw>=0)
        txt.spc()
        txt.print_bool(cbw<0)
        txt.spc()
        txt.print_bool(cbw<=0)
        txt.nl()

        txt.print("  calc'd: ")
        txt.print_w(sbw)
        txt.spc()
        txt.print_bool(sbw>0)
        txt.spc()
        txt.print_bool(sbw>=0)
        txt.spc()
        txt.print_bool(sbw<0)
        txt.spc()
        txt.print_bool(sbw<=0)
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
        if sbw>0 txt.print("true ") else txt.print("false ")
        if sbw>=0 txt.print("true ") else txt.print("false ")
        if sbw<0 txt.print("true ") else txt.print("false ")
        if sbw<=0 txt.print("true ") else txt.print("false ")
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

    sub bytes99() {
        const byte cb = 99
        byte @shared sb = 99
        byte @shared p = 100

        txt.print("\nsigned bytes, 99\n")
        txt.print("expected: ")
        txt.print_b(100)
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
        txt.print_b(100)
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
        txt.print_b(p)
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
        txt.print_b(100)
        txt.spc()
        if sb>100 txt.print("true ") else txt.print("false ")
        if sb>=100 txt.print("true ") else txt.print("false ")
        if sb<100 txt.print("true ") else txt.print("false ")
        if sb<=100 txt.print("true ") else txt.print("false ")
        txt.nl()

        txt.print(" ifstmt2: ")
        txt.print_b(p)
        txt.spc()
        if sb>p txt.print("true ") else txt.print("false ")
        if sb>=p txt.print("true ") else txt.print("false ")
        if sb<p txt.print("true ") else txt.print("false ")
        if sb<=p txt.print("true ") else txt.print("false ")
        txt.nl()
    }

    sub bytes100() {
        const byte cb = 100
        byte @shared sb = 100
        byte @shared p = 100

        txt.print("\nsigned bytes, 100\n")
        txt.print("expected: ")
        txt.print_b(100)
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
        txt.print_b(100)
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
        txt.print_b(p)
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
        txt.print_b(100)
        txt.spc()
        if sb>100 txt.print("true ") else txt.print("false ")
        if sb>=100 txt.print("true ") else txt.print("false ")
        if sb<100 txt.print("true ") else txt.print("false ")
        if sb<=100 txt.print("true ") else txt.print("false ")
        txt.nl()

        txt.print(" ifstmt2: ")
        txt.print_b(p)
        txt.spc()
        if sb>p txt.print("true ") else txt.print("false ")
        if sb>=p txt.print("true ") else txt.print("false ")
        if sb<p txt.print("true ") else txt.print("false ")
        if sb<=p txt.print("true ") else txt.print("false ")
        txt.nl()
    }

    sub bytes101() {
        const byte cb = 101
        byte @shared sb = 101
        byte @shared p = 100

        txt.print("\nsigned bytes, 101\n")
        txt.print("expected: ")
        txt.print_b(100)
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
        txt.print_b(100)
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
        txt.print_b(p)
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
        txt.print_b(100)
        txt.spc()
        if sb>100 txt.print("true ") else txt.print("false ")
        if sb>=100 txt.print("true ") else txt.print("false ")
        if sb<100 txt.print("true ") else txt.print("false ")
        if sb<=100 txt.print("true ") else txt.print("false ")
        txt.nl()

        txt.print(" ifstmt2: ")
        txt.print_b(p)
        txt.spc()
        if sb>p txt.print("true ") else txt.print("false ")
        if sb>=p txt.print("true ") else txt.print("false ")
        if sb<p txt.print("true ") else txt.print("false ")
        if sb<=p txt.print("true ") else txt.print("false ")
        txt.nl()
    }

    sub bytesoverflow() {
        byte @shared sb = -100
        byte @shared p = 100
        const byte cb = -100

        txt.print("\nsigned bytes, overflow\n")
        txt.print("expected: ")
        txt.print_b(100)
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
        txt.print_b(100)
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
        txt.print_b(p)
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
        txt.print_b(100)
        txt.spc()
        if sb>100 txt.print("true ") else txt.print("false ")
        if sb>=100 txt.print("true ") else txt.print("false ")
        if sb<100 txt.print("true ") else txt.print("false ")
        if sb<=100 txt.print("true ") else txt.print("false ")
        txt.nl()

        txt.print(" ifstmt2: ")
        txt.print_b(p)
        txt.spc()
        if sb>p txt.print("true ") else txt.print("false ")
        if sb>=p txt.print("true ") else txt.print("false ")
        if sb<p txt.print("true ") else txt.print("false ")
        if sb<=p txt.print("true ") else txt.print("false ")
        txt.nl()
    }

    sub bytessmall() {
        byte @shared sb = -10
        byte @shared p = 10
        const byte cb = -10

        txt.print("\nsigned bytes, small value\n")
        txt.print("expected: ")
        txt.print_b(10)
        txt.spc()
        txt.print_bool(cb>10)
        txt.spc()
        txt.print_bool(cb>=10)
        txt.spc()
        txt.print_bool(cb<10)
        txt.spc()
        txt.print_bool(cb<=10)
        txt.nl()

        txt.print("  calc'd: ")
        txt.print_b(10)
        txt.spc()
        txt.print_bool(sb>10)
        txt.spc()
        txt.print_bool(sb>=10)
        txt.spc()
        txt.print_bool(sb<10)
        txt.spc()
        txt.print_bool(sb<=10)
        txt.nl()

        txt.print("calc'd 2: ")
        txt.print_b(p)
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
        txt.print_b(10)
        txt.spc()
        if sb>10 txt.print("true ") else txt.print("false ")
        if sb>=10 txt.print("true ") else txt.print("false ")
        if sb<10 txt.print("true ") else txt.print("false ")
        if sb<=10 txt.print("true ") else txt.print("false ")
        txt.nl()

        txt.print(" ifstmt2: ")
        txt.print_b(p)
        txt.spc()
        if sb>p txt.print("true ") else txt.print("false ")
        if sb>=p txt.print("true ") else txt.print("false ")
        if sb<p txt.print("true ") else txt.print("false ")
        if sb<=p txt.print("true ") else txt.print("false ")
        txt.nl()
    }

    sub bytesoverflow_jump() {
        byte @shared sb = -100
        byte @shared p = 100
        const byte cb = -100

        txt.print("\nsigned bytes, overflow, jmp after if\n")
        txt.print("expected: ")
        txt.print_b(100)
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
        txt.print_b(100)
        txt.spc()
        if sb>100 goto jump1
        else { txt.print("false ") goto next1 }
jump1:
        txt.print("true ")
next1:
        if sb>=100 goto jump2
        else { txt.print("false ") goto next2 }
jump2:
        txt.print("true ")
next2:
        if sb<100 goto jump3
        else { txt.print("false ") goto next3 }
jump3:
        txt.print("true ")
next3:
        if sb<=100 goto jump4
        else { txt.print("false ") goto next4 }
jump4:
        txt.print("true ")
next4:
        txt.nl()

        txt.print("calc'd 2: ")
        txt.print_b(p)
        txt.spc()
        if sb>p goto jump1b
        else { txt.print("false ") goto next1b }
jump1b:
        txt.print("true ")
next1b:
        if sb>=p goto jump2b
        else { txt.print("false ") goto next2b }
jump2b:
        txt.print("true ")
next2b:
        if sb<p goto jump3b
        else { txt.print("false ") goto next3b }
jump3b:
        txt.print("true ")
next3b:
        if sb<=p goto jump4b
        else { txt.print("false ") goto next4b }
jump4b:
        txt.print("true ")
next4b:
        txt.nl()
    }

    sub bytesoverflow_jump_indirect() {
        byte @shared sb = -100
        byte @shared p = 100
        const byte cb = -100

        txt.print("\nsigned bytes, overflow, jmp indirect after if\n")
        txt.print("expected: ")
        txt.print_b(100)
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
        txt.print_b(100)
        txt.spc()
        uword tgt = &jump1
        if sb>100 goto tgt
        else { txt.print("false ") goto next1 }
jump1:
        txt.print("true ")
next1:
        tgt = &jump2
        if sb>=100 goto tgt
        else { txt.print("false ") goto next2 }
jump2:
        txt.print("true ")
next2:
        tgt = &jump3
        if sb<100 goto tgt
        else { txt.print("false ") goto next3 }
jump3:
        txt.print("true ")
next3:
        tgt = &jump4
        if sb<=100 goto tgt
        else { txt.print("false ") goto next4 }
jump4:
        txt.print("true ")
next4:
        txt.nl()

        txt.print("calc'd 2: ")
        txt.print_b(p)
        txt.spc()
        tgt = &jump1b
        if sb>p goto tgt
        else { txt.print("false ") goto next1b }
jump1b:
        txt.print("true ")
next1b:
        tgt = &jump2b
        if sb>=p goto tgt
        else { txt.print("false ") goto next2b }
jump2b:
        txt.print("true ")
next2b:
        tgt = &jump3b
        if sb<p goto tgt
        else { txt.print("false ") goto next3b }
jump3b:
        txt.print("true ")
next3b:
        tgt = &jump4b
        if sb<=p goto tgt
        else { txt.print("false ") goto next4b }
jump4b:
        txt.print("true ")
next4b:
        txt.nl()
    }

    sub bytessmall_jump() {
        byte @shared sb = -10
        byte @shared p = 10
        const byte cb = -10

        txt.print("\nsigned bytes, small value, jmp after if\n")
        txt.print("expected: ")
        txt.print_b(10)
        txt.spc()
        txt.print_bool(cb>10)
        txt.spc()
        txt.print_bool(cb>=10)
        txt.spc()
        txt.print_bool(cb<10)
        txt.spc()
        txt.print_bool(cb<=10)
        txt.nl()

        txt.print("  calc'd: ")
        txt.print_b(10)
        txt.spc()
        if sb>10 goto jump1
        else { txt.print("false ") goto next1 }
jump1:
        txt.print("true ")
next1:
        if sb>=10 goto jump2
        else { txt.print("false ") goto next2 }
jump2:
        txt.print("true ")
next2:
        if sb<10 goto jump3
        else { txt.print("false ") goto next3 }
jump3:
        txt.print("true ")
next3:
        if sb<=10 goto jump4
        else { txt.print("false ") goto next4 }
jump4:
        txt.print("true ")
next4:
        txt.nl()

        txt.print("calc'd 2: ")
        txt.print_b(p)
        txt.spc()
        if sb>p goto jump1b
        else { txt.print("false ") goto next1b }
jump1b:
        txt.print("true ")
next1b:
        if sb>=p goto jump2b
        else { txt.print("false ") goto next2b }
jump2b:
        txt.print("true ")
next2b:
        if sb<p goto jump3b
        else { txt.print("false ") goto next3b }
jump3b:
        txt.print("true ")
next3b:
        if sb<=p goto jump4b
        else { txt.print("false ") goto next4b }
jump4b:
        txt.print("true ")
next4b:
        txt.nl()
    }

    sub bytessmall_jump_indirect() {
        byte @shared sb = -10
        byte @shared p = 10
        const byte cb = -10

        txt.print("\nsigned bytes, small value, jmp indirect after if\n")
        txt.print("expected: ")
        txt.print_b(10)
        txt.spc()
        txt.print_bool(cb>10)
        txt.spc()
        txt.print_bool(cb>=10)
        txt.spc()
        txt.print_bool(cb<10)
        txt.spc()
        txt.print_bool(cb<=10)
        txt.nl()

        txt.print("  calc'd: ")
        txt.print_b(10)
        txt.spc()
        uword tgt = &jump1
        if sb>10 goto tgt
        else { txt.print("false ") goto next1 }
jump1:
        txt.print("true ")
next1:
        tgt = &jump2
        if sb>=10 goto tgt
        else { txt.print("false ") goto next2 }
jump2:
        txt.print("true ")
next2:
        tgt = &jump3
        if sb<10 goto tgt
        else { txt.print("false ") goto next3 }
jump3:
        txt.print("true ")
next3:
        tgt = &jump4
        if sb<=10 goto tgt
        else { txt.print("false ") goto next4 }
jump4:
        txt.print("true ")
next4:
        txt.nl()

        txt.print("calc'd 2: ")
        txt.print_b(p)
        txt.spc()
        tgt = &jump1b
        if sb>p goto tgt
        else { txt.print("false ") goto next1b }
jump1b:
        txt.print("true ")
next1b:
        tgt = &jump2b
        if sb>=p goto tgt
        else { txt.print("false ") goto next2b }
jump2b:
        txt.print("true ")
next2b:
        tgt = &jump3b
        if sb<p goto tgt
        else { txt.print("false ") goto next3b }
jump3b:
        txt.print("true ")
next3b:
        tgt = &jump4b
        if sb<=p goto tgt
        else { txt.print("false ") goto next4b }
jump4b:
        txt.print("true ")
next4b:
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
