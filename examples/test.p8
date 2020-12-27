%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit

main {

    ;uword cmap = memory("palette", 256*4)       ; only use 768 of these, but this allows re-use of the same block that the bmp module allocates
    uword num_colors = rnd()

    sub start () {

        txt.chrout('\n')
        txt.chrout('\n')
        test_stack.test()

    }

}
