main {
    const WIDTH=5
    const HEIGHT=8

    sub start() {
        ubyte[WIDTH] @shared tokens
        bool[2][4] @shared matrix1
        bool[2][HEIGHT] @shared matrix2
        bool[WIDTH][HEIGHT] @shared matrix3
    }
}
