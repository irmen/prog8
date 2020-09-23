; CommanderX16 simple graphics example!

%target cx16
%zeropage basicsafe

main {

    sub start() {

        void cx16.screen_set_mode($80)
        cx16.r0=0

        cx16.FB_init()
        cx16.GRAPH_init()
        cx16.r0 = 0
        cx16.r1 = 0
        cx16.FB_cursor_position()
        ubyte color
        repeat 320*199 {
            cx16.FB_set_pixel(color)
            color++
        }

        uword xx
        for xx in 0 to 319 step 32 {
            ubyte q
            for q in 0 to 31 {
                cx16.GRAPH_set_colors(q, 2, 0)
                cx16.r0 = xx+q
                cx16.r1=0
                cx16.r2=rnd()
                cx16.r3=199
                cx16.GRAPH_draw_line()
            }
        }
    }
}

