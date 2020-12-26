%import test_stack
%import gfx2
%zeropage basicsafe
%option no_sysinit

main {


    sub start () {

        void cx16.screen_set_mode($80)
        cx16.GRAPH_init(0)
        cx16.GRAPH_set_colors(13, 6, 6)
        cx16.GRAPH_clear()

        uword cp

        for cp in 0 to 15 {
            cx16.r0 = 10+cp*2
            cx16.r1 = 10+cp*11
            ubyte cc
            for cc in "Hello world, 123456789<>-=!#$%"
                cx16.GRAPH_put_next_char(cc)
        }

        test_stack.test()
    }

}
