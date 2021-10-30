%import floats
%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte xx
        float ff

        ff=0

        if ff==0 {
            txt.print("ff=0\n")
        }
        if ff!=0 {
            txt.print("ff!=0 (error!)\n")
        }
        ff=-0.22
        if ff==0 {
            txt.print("ff=0 (error!)\n")
        }
        if ff!=0 {
            txt.print("ff!=0\n")
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
