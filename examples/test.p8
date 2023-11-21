%import textio
%zeropage basicsafe

main {
    sub start() {
        %asm{{
            jsr p8_test
        }}
    }

    sub test() {
        cx16.r0++
    }
}
