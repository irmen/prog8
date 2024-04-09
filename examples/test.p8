%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        sys.clear_carry()
        cx16.r0s=-42

        if_z
            txt.print("zero")
        else if_cs
            txt.print("carry")
        else if_neg
            txt.print("negative")
        else
            txt.print("nothing")
    }
}

