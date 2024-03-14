%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ; TODO func is not called 4 times!!!!! FIX!!!
        if (not func(1))
            or (not func(1))
            or (not func(1))
            or (not func(1)) {
                txt.print("done1\n")
            }

        ; TODO func is not called 4 times!!!!! FIX!!!
        if func(2)
            and func(2)
            and func(2)
            and func(2) {
                txt.print("done2\n")
            }

        ; TODO func is not called 4 times!!!!! FIX!!!
        if func(3) and func(3) and func(3) and func(3) {
            txt.print("done3\n")
        }
    }

    sub func(ubyte x) -> bool {
        txt.print("func ")
        txt.print_ub(x)
        txt.nl()
        return true
    }
}
