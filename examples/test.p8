%import floats

main {

    struct List {
        ^^uword s
        ubyte n
        ^^List next
    }

    sub start() {
        ubyte[10] array
        uword @shared wordptr
        ^^bool @shared boolptr
        ^^float @shared floatptr
        ^^byte @shared byteptr
        ^^ubyte @shared ubyteptr
        ^^List @shared listptr
        ^^List @shared listptr2

        bool @shared zz
        float @shared fl
        byte @shared bb

        zz = boolptr[999]
        fl = floatptr[999]
        bb = byteptr[999]
        cx16.r0L = ubyteptr[999]
        cx16.r1L = wordptr[999]
        cx16.r2L = array[9]
    }
}
