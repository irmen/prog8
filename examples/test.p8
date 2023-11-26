%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        ubyte b = 4
        ubyte b2 = 4
        uword w = 4
        uword w2 = 4
        float c
        %asm {{
            nop
        }}
        c += b*b
        floats.print_f(c)
        txt.nl()
        c=0
        %asm {{
            nop
        }}
        c += w*w
        floats.print_f(c)
        txt.nl()
    }
}
