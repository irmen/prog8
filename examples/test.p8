main {
    sub start() {
        const long buffer = 2000
        const long bufferl = 999999
        uword @shared addr = &buffer[2]
        long @shared addr2 = &bufferl[2]
        const long width = 100
        uword @shared addr3 = &buffer[width]
        long @shared addr4 = &bufferl[width]
    }
}
