%import textio
%import diskio
%import floats
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {
    sub start() {
        ubyte bpp = 7
        uword num_colors = 1 << bpp     ; TODO FIX THIS

        txt.chrout('\n')
        txt.chrout('\n')
        txt.print_uw(num_colors)
        txt.chrout('\n')


        test_stack.test()
    }
}
