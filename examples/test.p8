%import textio
%zeropage basicsafe

main {
    sub start() {
        bench()
        txt.nl()

        uword total
        ubyte bb
        uword ww

        for bb in 0 to 255 {
            total = 0
            repeat bb
                total++
            txt.print_uw(total)
            txt.spc()
            txt.spc()
        }

        txt.nl()
        txt.nl()

        for ww in 0 to 600 {
            total = 0
            repeat ww
                total++
            txt.print_uw(total)
            txt.spc()
            txt.spc()
        }

    }


    sub iter(uword iterations) -> uword {
        uword total = 0
        repeat iterations {
            repeat iterations {
                total++
            }
        }

        return total

    }

    sub iterb(ubyte iterations) -> uword {
        uword total = 0
        repeat iterations {
            repeat iterations {
                total++
            }
        }

        return total
    }

    sub bench() {
        uword xx1
        uword xx2
        uword xx3
        uword iterations

        xx1=0
        repeat 99 {
            xx1++
        }
        txt.print_uw(xx1)   ;99
        txt.nl()

        xx1 = iterb(10)   ; 100
        txt.print_uw(xx1)
        txt.nl()
        xx1 = iterb(1)   ; 1
        txt.print_uw(xx1)
        txt.nl()
        xx1 = iterb(0)   ; 0
        txt.print_uw(xx1)
        txt.nl()
        txt.nl()

        xx1 = iter(0)
        txt.print_uw(xx1)   ; 0
        txt.nl()
        xx1 = iter(10)
        txt.print_uw(xx1)   ; 100
        txt.nl()
        xx1 = iter(16)
        txt.print_uw(xx1)   ; 256
        txt.nl()
        xx1 = iter(20)
        txt.print_uw(xx1)   ; 400
        txt.nl()
        xx1 = iter(200)
        txt.print_uw(xx1)   ; 4000
        txt.nl()
        xx1 = iter(600)
        txt.print_uw(xx1)   ; 32320
        txt.nl()
        txt.nl()


        c64.SETTIM(0,0,0)

        xx1=0
        xx2=0
        xx3=0
        iterations = 600
        repeat 600 {
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
