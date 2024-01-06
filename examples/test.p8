%import textio
%import string
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword @shared uw = $3f2f

        if uw & $0800
            txt.print("ok1\n")

        if uw & 8
            txt.print("ok2\n")

        if uw & $0800 ==0
            txt.print("fail1\n")

        if uw & $0800 !=0
            txt.print("ok3\n")

        if uw & 8 ==0
            txt.print("fail2\n")

        if uw & 8 !=0
            txt.print("ok4\n")



        if uw & $ff00 == $3f00
            txt.print("ok5\n")

        if uw & $ff00 != $3f00
            txt.print("fail5\n")

        if uw & $00ff == $002f
            txt.print("ok6\n")

        if uw & $00ff != $002f
            txt.print("fail6\n")
    }
}
