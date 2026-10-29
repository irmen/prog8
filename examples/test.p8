main {
    sub rotate_90_left(ubyte dir) -> ubyte {
        when dir {
            1 -> return 11
            2 -> return 22
            3 -> return 33
            4 -> return 44
            else -> cx16.r0++
        }
    }

    sub start() {
        cx16.r0L = rotate_90_left(22)
    }
}
