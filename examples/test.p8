%import textio
%zeropage basicsafe

; TODOs:
; empty struct declartion gives "struct must contain at least one field" error, a similar error must be given for empty enum declarations   enum Color { }

main {

    sub start() {
        cx16.r0 = other.Color::GREEN      ; ERROR: private enum member
        other.bvar = 42                    ; ERROR: private var
        other.derp()                       ; ERROR: private asmsub
        ; other.derp2()                    ; ERROR: private extsub (disabled because extsub resolution issues)
        cx16.r0 = other.allowed_val        ; allowed (public const)
        other.allowed_sub()                ; allowed (public sub)
    }
}

other {

    private bool @shared bvar

    private struct Node {
        bool flag
    }

    private enum Color {
        RED = 10,
        GREEN = 20,
        BLUE = 30
    }

    private asmsub derp() {
        %asm {{
            nop
            rts
        }}
    }

    private extsub $fede = derp2()

    const ubyte allowed_val = 99
    sub allowed_sub() {
        cx16.r0 = Color::GREEN            ; allowed: accessing private const from same block
    }
}
