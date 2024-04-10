%zeropage basicsafe
%import textio

main {
    sub start() {
        uword @shared a,b
        b = a                   ; works
        cx16.r1L = lsb(a)       ; works
        funcw(a)                ; works
        funcb(lsb(a))           ; fails :-(
    }

    sub funcw(uword arg) {
        arg++
    }

    sub funcb(ubyte arg) {
        arg++
    }
}
