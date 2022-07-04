%import textio
%zeropage basicsafe

main  {

    sub value(word input) -> word {
        return input-999
    }

    sub boolfunc(bool data) -> bool {
        return not data
    }

    sub start() {

        ubyte ubb

        if ubb and 10
            ubb++


        bool b1 = 1
        bool b2 = 0
        bool b3 = true
        bool b4 = false
        bool bb5 = -99
        bool bb6 = 123

        txt.print_ub(b1)
        txt.spc()
        txt.print_ub(b2)
        txt.spc()
        txt.print_ub(b3)
        txt.spc()
        txt.print_ub(b4)
        txt.spc()
        txt.print_b(bb5)
        txt.spc()
        txt.print_b(bb6)
        txt.nl()

        b1 = value(99) as bool
        txt.print_ub(b1)                    ; should be  1
        txt.spc()
        txt.print_ub(value(99) as ubyte)    ; should be 124
        txt.spc()
        txt.print_ub(value(99) as bool)     ; should be 1
        txt.nl()

        txt.print_ub(boolfunc(true))
        txt.spc()
        txt.print_ub(boolfunc(false))
        txt.nl()

        ubb = ubb != 0
        ubb++
        ubb = bb6 != 0
        txt.print_ub(ubb)
        txt.nl()
     }
}
