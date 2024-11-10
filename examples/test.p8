%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        repeat 100 {
            cx16.r0++
            if cx16.r0!=0
                goto mylabel
        }

mylabel:

    }
}
