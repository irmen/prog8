%import textio

main {
    sub start() {
        txt.chrout('a')
        txt.chrout('b')
        txt.chrout('c')
        txt.chrout('d')
        txt.chrout('\n')

        ubyte cc
        for cc in "Hello from Prog8 on a m68000 system!\n"
            txt.chrout(cc)
    }
}
