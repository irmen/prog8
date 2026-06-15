%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword[2]  wordarray
        cx16.r0 = func()

        sub func() -> uword {
            cx16.r0++
            return wordarray
        }
    }
}
