%import textio
%import string

%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        screen.text_colors = [6,5,4,3,2,1]

        for cx16.r0L in screen.text_colors {
            txt.print_ub(cx16.r0L)
            txt.spc()
        }
        txt.nl()
    }
}

screen {
    ubyte[6] text_colors
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
