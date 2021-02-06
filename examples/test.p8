%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte bank

        ; TODO give error that a register is used as an argument value and at the same time is a register parameter taking a new value.
        ;      (note: this is OK if the  register is used as an argument for a register param that is the same register)
        cx16.vaddr(lsb(cx16.r1), cx16.r0, 0, 1)

        txt.print("hello")
    }
}
