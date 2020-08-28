; CommanderX16 text clock example!
; make sure to compile with the cx16 compiler target.

%zeropage basicsafe

main {

    sub start() {

        cx16.screen_set_mode($80)
        cx16.r0=0
        cx16.GRAPH_init()
        cx16.GRAPH_set_colors(0, 0, 0)

        uword xx
        for xx in 0 to 319 step 32 {
            cx16.GRAPH_clear()
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

