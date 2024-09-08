%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared x
        uword @shared w

        x=2
        if x==2 or cx16.r0L==42
            txt.print("yep0\n")

        if x==1 or x==2
            txt.print("yep1\n")

        x = 9
        if x==1 or x==2
            txt.print("yep2-shouldn't-see-this\n")      ; TODO fix this in IR/VM

        if x in 1 to 4
            txt.print("yep3-shouldn't-see-this\n")
        if x in 4 to 10
            txt.print("yep4\n")


        w=2222
        if w==1111 or w==2222
            txt.print("w-yep1\n")

        w = 4999
        if w==1111 or w==2222
            txt.print("w-yep2-shouldn't-see-this\n")      ; TODO fix this in IR/VM

        if w in 1111 to 4444
            txt.print("w-yep3-shouldn't-see-this\n")
        if w in 4444 to 5555
            txt.print("w-yep4\n")
    }
}
