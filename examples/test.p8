main {
    sub start() {
        ubyte @shared v1, v2
        if v1==0 and v2 & 3 !=0
            v1++
    }
}
