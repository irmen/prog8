%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub func(bool flag @R1) {
        if flag
            return
    }

    extsub $2000 = extok(bool flag @R0)

    sub start() {
        func(true)
        extok(true)
    }
}
