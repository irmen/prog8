%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        rsavex()

        ubyte @shared ub
        main.normalfoo.arg=99
        void normalfoo(42)
somelabel:
        ub++
        txt.print_ub(ub)
    }

    sub normalfoo(ubyte arg) -> ubyte {
        arg++
        return 42
    }
}
