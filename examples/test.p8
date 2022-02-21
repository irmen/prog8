%launcher none

main {
    sub start() {
        uword @shared names_buffer = memory("filenames", 200, 0)

        do {
            ubyte @shared char = '*'
        } until true
    }
}
