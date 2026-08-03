%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword @shared x = 41
        uword @shared y = x % 16      ; expect 41 & 15 = 9
        txt.print("U=")
        txt.print_uw(y)
        txt.print(" ")

        word @shared z = -1
        word @shared w = z % 16      ; signed: expect -1 (must stay %)
        txt.print("S=")
        txt.print_w(w)
        txt.print("\n")
    }
}
