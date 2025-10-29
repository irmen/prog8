;%zeropage basicsafe
;%import textio
;
;main {
;    sub start() {
;        ^^uword @shared ptr = 7000
;        const ^^uword cptr = 8000            ; TODO fix type error; loses pointer
;        ptr^^ = 12345
;        cptr^^ = 12345
;        txt.print_uw(peekw(7000))
;        txt.spc()
;        txt.print_uw(peekw(8000))
;    }
;}


; TYPE CAST CRASH:
;%zeropage basicsafe
;%import strings
;%import textio
;
;main {
;    struct Line {
;        ^^Line prev
;        ^^Line next
;        ^^ubyte text
;    }
;    uword buffer = memory("buffer", 100*sizeof(Line), 1)
;    ^^Line next = buffer
;
;    sub start() {
;        ^^Line line = next
;        next += 1
;        line.text = next as ^^ubyte        ; TODO fix crash here
;        next = (next as uword) + 81
;        txt.print_uwhex(buffer, true) txt.nl()
;        txt.print_uwhex(next, true) txt.nl()
;        txt.print_uwhex(line, true) txt.nl()
;        txt.print_uwhex(line.text, true) txt.nl()
;    }
;}


%import floats
%import textio
%zeropage basicsafe

main {
    sub start() {
        bytetest()
        wordtest()
        longtest()
        floattest()         ; TODO fix invalid 6502 code gen /crash
    }

    sub bytetest() {
        ubyte[] foo = [11, 22, 33]
        ubyte i
        txt.print("before:")
        for i in 0 to 2 {
            txt.chrout(' ')
            txt.print_ub(foo[i])
        }
        txt.nl()
        foo[2] = foo[1]
        foo[1] = foo[0]
        foo[0] = 0
        txt.print(" after:")
        for i in 0 to 2 {
            txt.chrout(' ')
            txt.print_ub(foo[i])
        }
        txt.nl()
    }

    sub wordtest() {
        uword[] foo = [1111, 2222, 3333]
        ubyte i
        txt.print("before:")
        for i in 0 to 2 {
            txt.chrout(' ')
            txt.print_uw(foo[i])
        }
        txt.nl()
        foo[2] = foo[1]
        foo[1] = foo[0]
        foo[0] = 0
        txt.print(" after:")
        for i in 0 to 2 {
            txt.chrout(' ')
            txt.print_uw(foo[i])
        }
        txt.nl()
    }

    sub longtest() {
        long[] foo = [111111, 222222, 333333]
        ubyte i
        txt.print("before:")
        for i in 0 to 2 {
            txt.chrout(' ')
            txt.print_l(foo[i])
        }
        txt.nl()
        foo[2] = foo[1]
        foo[1] = foo[0]
        foo[0] = 0
        txt.print(" after:")
        for i in 0 to 2 {
            txt.chrout(' ')
            txt.print_l(foo[i])
        }
        txt.nl()
    }

    sub floattest() {
        float[] foo = [1.1, 2.2, 3.3]
        ubyte i
        txt.print("before:")
        for i in 0 to 2 {
            txt.chrout(' ')
            txt.print_f(foo[i])
        }
        txt.nl()
        foo[2] = foo[1]
        foo[1] = foo[0]
        foo[0] = 0
        txt.print(" after:")
        for i in 0 to 2 {
            txt.chrout(' ')
            txt.print_f(foo[i])
        }
        txt.nl()
    }
}
