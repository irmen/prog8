%import textio

main {
    ubyte @shared joy_info

    sub start() {
        void pushing_start()
    }

    sub pushing_start() -> ubyte {
        joy_info++
        return not c64.READST()
    }

    sub derp(ubyte aa) -> ubyte {
        aa++
        return aa*2
    }
}
