main {
    ^^uword g_wptr

    sub start() {
        ^^uword l_wptr

        cx16.r0 = g_wptr
        cx16.r1 = g_wptr^^
        cx16.r0 = l_wptr
        cx16.r1 = l_wptr^^
    }
}
