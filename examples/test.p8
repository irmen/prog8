%option enable_floats

main {
    struct Node {
        ubyte id
        str name
        uword array
        bool flag
        float perc
    }
    struct Foobar {
        bool thing
    }

    sub start() {
        ^^Node test = []

        test.id ++
        test.array += 1000
        test.id <<= 2
        test.id <<= cx16.r0L
        test.id >>= 3
        test.id >>= cx16.r0L
        test.id &= 1
;        test.id *= 5        ; TODO implement this
;        test.id /= 5        ; TODO implement this
        test.array ^= 1000
        test.array |= 1000
        test.array &= 1000
        test.array >>= 3
        test.array >>= cx16.r0L
        test.array <<= 2
        test.array <<= cx16.r0L
        test.array *= 5
    }
}
