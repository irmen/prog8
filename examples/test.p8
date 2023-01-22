%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        ubyte @shared ub = asmfoo(42)
        ub = normalfoo(42)
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
