main {
    sub start() {
        uword[256] derp

        derp[200] = 22

        cx16.r0 = &start
        cx16.r1 = &&start
    }
}
