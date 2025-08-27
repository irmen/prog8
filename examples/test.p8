main {
    sub start() {
        ^^ubyte @shared ptr = 2000
        cx16.r0L = 10
        ptr += cx16.r0L

        if ptr==0
            cx16.r0L++

        if ptr!=0
            cx16.r0L++

        if ptr==9999
            cx16.r0L++

        if ptr!=9999
            cx16.r0L++

        if ptr==cx16.r0+2000
            cx16.r0L++

        if ptr!=cx16.r0+2000
            cx16.r0L++

    }
}
