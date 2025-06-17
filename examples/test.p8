main {
    sub start() {
        uword uw = 9999
        word sw = -2222
        ubyte ub = 42
        byte sb = -99
        bool bb = true

        cx16.r0 = uw
        cx16.r0s = sw
        cx16.r0L = ub
        cx16.r0H = ub
        cx16.r0sL = sb
        cx16.r0sH = sb
        cx16.r0bL = bb
        cx16.r0bH = bb

        uw = cx16.r0
        sw = cx16.r0s
        ub = cx16.r0L
        ub = cx16.r0H
        sb = cx16.r0sL
        sb = cx16.r0sH
        bb = cx16.r0bL
        bb = cx16.r0bH
    }
}
