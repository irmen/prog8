%import textio
%zeropage basicsafe

main {
    sub start() {
        uword ptr = &test
        call(ptr)
        call(ptr)
        call(ptr)
    }

    sub test() {
        txt.print("test!\n")
    }
}
