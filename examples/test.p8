%option enable_floats
main {
    sub start() {
        float[] cs = 1 to 42 ; values are computed at compile time
        cs[0] = 23 ; keep optimizer from removing it
    }
}
