main {

    sub start() {
        uword xx = 100
        uword yy = xx / 256     ; TODO hangs in optimizer for 6502
        yy++
    }
}
