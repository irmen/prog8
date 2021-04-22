%import textio
%import string
%zeropage dontuse
%import test_stack

main {

    sub start() {
        uword zz = $4000

        txt.print("hello")

        txt.print_uwhex(peekw(zz+2), true)

        @(zz+2) = lsb($ea31)
        @(zz+3) = msb($ea31)
        pokew(zz+2, $ea32)          ; TODO fix crash
        zz = peekw(zz+2)        ; TODO fix crash with asm
        txt.print_uwhex(zz, true)
    }

}

