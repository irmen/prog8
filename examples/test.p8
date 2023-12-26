%import textio
%zeropage basicsafe

main {
    sub start() {
        cx16.r0++
        str[] names = ["irmen", "de", "jong"]
        uword zz = names[1]
        txt.print(names[1])
    }

    sub derp() {
        cx16.r0++
    }

    asmsub hurrah() {
        %ir {{
            nop
        }}
    }
}
