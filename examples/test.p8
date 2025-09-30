main {
    sub start() {
        uword @shared value

        if msb(value)>0
            cx16.r0++

        if lsb(value)>0
            cx16.r0++

        value = mkword(cx16.r0L, cx16.r1L)
        if_z
            cx16.r0++
    }
}
