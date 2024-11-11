%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        defer initial()
        if allocate() {
            txt.print("1")
            defer deallocate()
            txt.print("2")
            return
        }
        txt.print("3")
    }

    sub allocate() -> bool {
        txt.print("allocate\n")
        return true
    }

    sub initial() {
        txt.print("initial\n")
    }
    sub deallocate() {
        txt.print("deallocate\n")
    }
}
