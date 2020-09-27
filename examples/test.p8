%target c64
%import textio
%import syslib
%zeropage basicsafe


main {
    sub start() {
        withX(1)
        withX(2)
        withX(3)
        withY(6)
        withY(7)
        withY(8)
    }

    asmsub withX(ubyte foo @X) {
        %asm {{
            rts
        }}
    }
    asmsub withY(ubyte foo @Y) {
        %asm {{
            rts
        }}
    }
}
