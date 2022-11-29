%import textio
%import cx16logo

nop {
    sub lda(ubyte sec) -> ubyte {
asl:
        ubyte brk = sec
        sec++
        brk += sec
        return brk
    }
}

main {

    sub ffalse(ubyte arg) -> ubyte {
        arg++
        return 0
    }
    sub ftrue(ubyte arg) -> ubyte {
        arg++
        return 128
    }

    sub start() {
        ubyte col = 10
        ubyte row = 20
        cx16logo.logo_at(col, row)
        txt.setcc(10, 10, 2, 3)
        txt.print_ub(nop.lda(42))
        txt.nl()
        txt.print_uw(nop.lda.asl)

        void ffalse(99)
        void ftrue(99)
    }
}
