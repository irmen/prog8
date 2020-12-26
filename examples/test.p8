%import test_stack
%import gfx2
%zeropage basicsafe
%option no_sysinit

main {


    sub start () {

        ;gfx2.screen_mode(0)
        gfx2.screen_mode(255)

        uword address
        for address in gfx2.charset_addr to gfx2.charset_addr+4*8-1 {
            txt.print_ubbin(cx16.vpeek(gfx2.charset_bank, address),0)
            txt.chrout('\n')
        }

        test_stack.test()
    }

}
