%import textio
%import math
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte[5] cave_times
        ubyte[5] diamonds_needed

        cave_times = [1,2,3,4,5]
        diamonds_needed = [1,2,3,4,5]

        for cx16.r0L in 0 to len(cave_times)-1 {
            txt.print_ub(cave_times[cx16.r0L])
            txt.spc()
        }
        txt.nl()
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
