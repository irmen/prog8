%import textio
%zeropage basicsafe

main {
    sub start() {
        struct Selector {
            ubyte index     ; selected value (what is visible)
            ^^pointer choices   ; ideally both
        }

        ^^Selector item = []
        txt.print(item.choices[item.index])
    }
}
