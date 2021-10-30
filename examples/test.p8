%import textio
%zeropage basicsafe

main {

    sub start() {
        byte xx

        xx=0

        if xx>=0 {
            txt.print("xx>=0\n")
        } else {
            txt.print("error1\n")
        }
        if xx<=0 {
            txt.print("xx<=0\n")
        } else {
            txt.print("error1qq\n")
        }
        if xx>0 {
            txt.print("xx>0 error\n")
        } else {
            txt.print("ok1\n")
        }
        if xx<0 {
            txt.print("xx<0 error\n")
        } else {
            txt.print("ok1\n")
        }
        txt.nl()

        xx=22
        if xx>=0 {
            txt.print("xx>=0\n")
        } else {
            txt.print("error2\n")
        }
        if xx<=0 {
            txt.print("xx<=0 error\n")
        } else {
            txt.print("ok2\n")
        }
        if xx>0 {
            txt.print("xx>0\n")
        } else {
            txt.print("error2\n")
        }
        if xx<0 {
            txt.print("xx<0 error\n")
        } else {
            txt.print("ok2\n")
        }
        txt.nl()

        xx=-11
        if xx>=0 {
            txt.print("xx>=0 error\n")
        } else {
            txt.print("ok3\n")
        }
        if xx<=0 {
            txt.print("xx<=0\n")
        } else {
            txt.print("error3\n")
        }
        if xx>0 {
            txt.print("xx>0 error\n")
        } else {
            txt.print("ok3\n")
        }
        if xx<0 {
            txt.print("xx<0\n")
        } else {
            txt.print("error3\n")
        }



        if xx {             ; doesn't use stack...
            xx++
        }

        xx = xx+1           ; doesn't use stack...

        if 8<xx {
        }

        if xx+1 {             ; TODO why does this use stack?
            xx++
        }

        xx = xx & %0001     ; doesn't use stack...

        if xx & %0001 {     ; TODO why does this use stack?
            xx--
        }

        do {
            xx++
        } until xx+1

        while xx+1 {
            xx++
        }
    }
}
