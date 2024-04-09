%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        const ubyte x=0

        if x==3 {
            txt.print("three")
        } else if x==4 {
            txt.print("four")
        }
    }
}

