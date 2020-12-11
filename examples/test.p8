%import textio
%import diskio
%import floats
%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {
    sub start () {

        uword bitmap_load_address = progend()
        uword max_bitmap_size = $9eff - bitmap_load_address         ; TODO why is this not optimized away?

        uword xx = progend()
        txt.print_uwhex(xx, 1)
        txt.print_uwhex(progend(), 1)

        test_stack.test()
    }

}
