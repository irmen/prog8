%import math
%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte lda = getrandom()
        lda++
        cx16.r0 = (math.rnd() % 20) * $0010
        lda = math.rnd() % 5
        lda++
    }

    sub getrandom() -> ubyte {
        %asm {{
            lda  #42
            rts
        }}
    }
}

