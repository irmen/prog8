%import textio
%import petsnd
%zeropage basicsafe

main {
    sub start() {
        petsnd.on()

        txt.print("c ")
        petsnd.note(petsnd.C_4)
        sys.wait(30)
        txt.print("e ")
        petsnd.note(petsnd.E_4)
        sys.wait(30)
        txt.print("a ")
        petsnd.note(petsnd.A_4)
        sys.wait(30)

        petsnd.off()
    }
}
