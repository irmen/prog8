%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        rsavex()

        ubyte @shared ub
        void  asmfoo(42)
        main.asmfoo.arg = 42
        main.normalfoo.arg=99
        ; normalfoo(42)
somelabel:
        ub++
        txt.print_ub(ub)
    }

    asmsub asmfoo(ubyte arg @Y) -> ubyte @Y {
        %asm {{
            iny
            rts
        }}
    }

    sub normalfoo(ubyte arg) -> ubyte {
        arg++
        return 42
    }
}
