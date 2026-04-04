%import textio
%zeropage basicsafe
%encoding petscii

main {
    sub start() {
        uword err1, err2
        uword val

        err1 = eval1("42")
        cx16.r0++
        err1, val, err2 = eval(0)
        if err1 == 0 {
            val += 1
        }

        txt.print(err1)
        txt.nl()
        txt.print(err2)
        txt.nl()
        txt.print_uw(val)
        txt.nl()
    }

    sub eval1(str expr) -> str {
        if expr == 0 {
            return "empty"
        }
        return 0
    }

    sub eval(str expr) -> str, uword, str {
        if expr == 0 {
            return "empty", 42, "empty2"
        }
        return 0, 42, 0
    }
}
