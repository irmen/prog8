%option enable_floats

main {
    sub start() {
        ubyte @shared @zp var1 = 42
        uword @shared @zp var2 = 4242
        str @shared name = "irmen"
        ubyte[] @shared array1 = [11,22,33,44]
        uword[5] @shared array2 = 9999
        uword[5] @shared array3
        float @shared fvar = 1.234
        float @shared fvar2
        float[] @shared farray1 = [1.11,2.22,3.33]
        float[5] @shared farray2 = 999.9
        float[5] @shared farray3
    }
}
