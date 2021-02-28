%import textio
%zeropage basicsafe

main {

    sub start() {
        uword xx1
        uword xx2
        uword xx3
        uword iterations

        c64.SETTIM(0,0,0)

        iterations = 0
        repeat iterations {
            repeat iterations {
                xx1++
                xx2++
                xx3++
            }
        }

        iterations = 600
        repeat iterations {
            repeat iterations {
                xx1++
                xx2++
                xx3++
            }
        }
        uword time = c64.RDTIM16()
        txt.print("time: ")
        txt.print_uw(time)
        txt.print("\n$7e40? :\n")
        txt.print_uwhex(xx1,true)
        txt.nl()
        txt.print_uwhex(xx2,true)
        txt.nl()
        txt.print_uwhex(xx3,true)
    }
}
