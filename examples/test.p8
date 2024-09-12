%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool @shared b = true
        ubyte @shared x = 42
        uword @shared w = 9999

        if b in [true, false, false, true]
            txt.print("yep0\n")

        if x==13 or x==42               ; why is there shortcut-evaluation here for those simple terms
            txt.print("yep1\n")

        if x in [13, 42,99,100]
            txt.print("yep2\n")

        if x==13 or x==42 or x==99 or x==100    ; optimize the containment check to not always use an array + jsr
            txt.print("yep3\n")

        if w==1313 or w==4242 or w==9999 or w==10101     ; optimize the containment check to not always use an array + jsr
            txt.print("yep4\n")

        if w==1313 or w==9999     ; optimize the containment check to not always use an array + jsr
            txt.print("yep5\n")
    }
}
