%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte a = 99
        cx16.r0 = foo()
        cx16.r0 = cx16.r1 % 4
        cx16.r0 = cx16.r1 %a

        %asm {{
            rts
        }}

        %ir {{
            yohoo
        }}
    }

    extsub $f000 = foo() clobbers(X) -> uword @AY
}
