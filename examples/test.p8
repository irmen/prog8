%import textio
%import math
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword[7] rolls

        txt.print("wait...\n")
        repeat 30 {
            repeat 1000 {
                unroll 10 rolls[math.rnd() % len(rolls)]++
            }
        }

        for cx16.r0L in 0 to len(rolls)-1 {
            txt.print_ub(cx16.r0L)
            txt.spc()
            txt.print_uw(rolls[cx16.r0L])
            txt.nl()
        }

        txt.print("wait...\n")
        rolls = [0,0,0,0,0,0,0]
        repeat 30 {
            repeat 1000 {
                unroll 10 rolls[math.randrange(len(rolls))]++
            }
        }

        for cx16.r0L in 0 to len(rolls)-1 {
            txt.print_ub(cx16.r0L)
            txt.spc()
            txt.print_uw(rolls[cx16.r0L])
            txt.nl()
        }
    }
}


/*main222 {
    sub start() {

        str name1 = "name1"
        str name2 = "name2"
        uword[] @split names = [name1, name2, "name3"]
        uword[] addresses = [0,1,2]
        names = [1111,2222,3333]

        for cx16.r0 in names {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()

        addresses = names

        for cx16.r0 in addresses {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()

        names = [9999,8888,7777]
        names = addresses
        for cx16.r0 in names {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
    }
}*/
