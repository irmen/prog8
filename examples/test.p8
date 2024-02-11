%import textio
%import math
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte[256] @shared arr1 = 99
        ubyte[256] @shared arr2 = 0
        uword[128] @shared warr1 = 9999
        uword[128] @shared warr2 = 0

        txt.print_ub(all(arr2))
        txt.nl()
        txt.print_ub(all(warr2))
        txt.nl()
        arr2 = arr1
        warr2 = warr1
        txt.print_ub(all(arr2))
        txt.nl()
        txt.print_ub(all(warr2))
        txt.nl()


        uword[] @split cave_times = [1111,2222,3333,4444]
        cave_times = [9999,8888,7777,6666]

        for cx16.r0L in 0 to len(cave_times)-1 {
            txt.print_uw(cave_times[cx16.r0L])
            txt.spc()
        }
        txt.nl()

        ubyte[] cave_times2 = [11,22,33,44]
        cave_times2 = [99,88,77,66]

        for cx16.r0L in 0 to len(cave_times2)-1 {
            txt.print_ub(cave_times2[cx16.r0L])
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
