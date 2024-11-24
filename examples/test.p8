%import textio
%zeropage basicsafe

main {
    sub start() {
        foo(42)
        bar(9999,55)
        txt.nl()
        test(1,2,3)
        test2(1)
    }

    sub foo(ubyte arg) {
        txt.print_ub(arg)
        txt.nl()
    }

    sub bar(uword arg, ubyte arg2) {
        txt.print_uw(arg)
        txt.spc()
        txt.print_ub(arg2)
        txt.nl()
    }

    asmsub test(ubyte a1 @R1, ubyte a2 @R1, ubyte a3 @R2) {      ; TODO should give register reuse error
        %asm {{
            rts
        }}
    }

    asmsub test2(uword a1 @AY) clobbers(A, X) -> ubyte @X {
        %asm {{
            rts
        }}
    }
}
