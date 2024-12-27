%zeropage basicsafe
%option no_sysinit

main {
    ubyte @shared width

    sub start() {
        if width==22 or width==33 {
            cx16.r1++
        }
    }

}

