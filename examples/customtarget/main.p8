%import syslib
%zeropage basicsafe

main {
    sub start() {
        ubyte char

        sys.CHROUT(14)  ; lowercase
        for char in "\nHello, World!\n" {
            sys.CHROUT(char)
        }
    }
}
