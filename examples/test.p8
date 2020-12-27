%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit

main {


    sub start () {
        uword length

        if length>256 {
            repeat length-1
                gfx2.next_pixel(color)
        } else {
            repeat (length-1) as ubyte      ; TODO lsb(length-1) doesn't work!?!?!?
                gfx2.next_pixel(color)
        }

        test_stack.test()

    }

}
