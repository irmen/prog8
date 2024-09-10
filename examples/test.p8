%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        ubyte @shared x

;        if x==13 or x==42               ; why is there shortcut-evaluation here for those simple terms
;            cx16.r0L++

        if x==13 or x==42 or x==99 or x==100     ; is this really more efficient as a containment check , when the shortcut-evaluation gets fixed??
            cx16.r0L++
    }
}
