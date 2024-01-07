%import textio
%zeropage basicsafe
%option no_sysinit

mpb {
    const uword HIGH_MEMORY_START = $A000
}

main {
    &uword[20] wa = mpb.HIGH_MEMORY_START
;    &uword[20] wa = $A000

    sub start() {
        wa[0] = "smile"
        txt.print(wa[0])
    }
}
