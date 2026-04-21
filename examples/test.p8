main {
    sub start() {
        ubyte a=10
        cx16.r0++
        a = 20
        cx16.r0 = a
        a = 30
        cx16.r1 = a
    }
}
