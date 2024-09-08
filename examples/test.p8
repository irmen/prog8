%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        byte @shared x
        word @shared w

        if x==0 or x==1 or x==2
            x++

        if w==0 or w==1 or w==2
            x++
    }
}
