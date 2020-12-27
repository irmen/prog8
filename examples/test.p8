%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {

        thing()
        thing()
        thing()
        thing()
        thing()
        test_stack.test()

        sub thing() -> ubyte {
            uword buffer = memory("buffer", 512)
            uword buffer2 = memory("buffer", 512)
            uword buffer3 = memory("cache", 20)

            txt.print_uwhex(buffer, true)
            txt.chrout('\n')
            txt.print_uwhex(buffer2, true)
            txt.chrout('\n')
            txt.print_uwhex(buffer3, true)
            txt.chrout('\n')
            buffer+=$1111
            buffer2+=$1111
            buffer3+=$1111
            txt.print_uwhex(buffer, true)
            txt.chrout('\n')
            txt.print_uwhex(buffer2, true)
            txt.chrout('\n')
            txt.print_uwhex(buffer3, true)
            txt.chrout('\n')
            txt.chrout('\n')
            return 0
        }
    }

}
