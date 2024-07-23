%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        ubyte @shared variable

        variable = 0
        while variable & %10000000 == 0 {
            cx16.r0L++
            variable = 128
        }
        txt.chrout('1')
        while variable & %10000000 != 0 {
            cx16.r0L++
            variable = 0
        }
        txt.chrout('2')
        while variable & %01000000 == 0 {
            cx16.r0L++
            variable = 64
        }
        txt.chrout('3')
        while variable & %01000000 != 0 {
            cx16.r0L++
            variable=0
        }
        txt.chrout('4')
        variable = 255
        while variable & %10000000 == 0 {
        }
        while variable & %01000000 == 0 {
        }
        txt.chrout('5')
        variable = 0
        while variable & %10000000 != 0 {
        }
        while variable & %01000000 != 0 {
        }
        txt.chrout('6')
        txt.chrout('\n')

        variable = 0
        cx16.r0L++
        if variable & %10000000 == 0 {
            txt.print("bit 7 not set\n")
        }
        if variable & %10000000 != 0 {
            txt.print("bit 7 set\n")
        }
        if variable & %10000000 == 0 {
            txt.print("bit 7 not set\n")
        } else {
            txt.print("bit 7 set\n")
        }
        if variable & %10000000 != 0 {
            txt.print("bit 7 set\n")
        } else {
            txt.print("bit 7 not set\n")
        }

        variable = 128
        cx16.r0L++
        if variable & %10000000 == 0 {
            txt.print("bit 7 not set\n")
        }
        if variable & %10000000 != 0 {
            txt.print("bit 7 set\n")
        }
        if variable & %10000000 == 0 {
            txt.print("bit 7 not set\n")
        } else {
            txt.print("bit 7 set\n")
        }
        if variable & %10000000 != 0 {
            txt.print("bit 7 set\n")
        } else {
            txt.print("bit 7 not set\n")
        }

        if variable & %01000000 == 0 {
            txt.print("bit 6 not set\n")
        }
        if variable & %01000000 != 0 {
            txt.print("bit 6 set\n")
        }
        if variable & %01000000 == 0 {
            txt.print("bit 6 not set\n")
        } else {
            txt.print("bit 6 set\n")
        }
        if variable & %01000000 != 0 {
            txt.print("bit 6 set\n")
        } else {
            txt.print("bit 6 not set\n")
        }
        variable = %01000000
        cx16.r0L++
        if variable & %01000000 == 0 {
            txt.print("bit 6 not set\n")
        }
        if variable & %01000000 != 0 {
            txt.print("bit 6 set\n")
        }
        if variable & %01000000 == 0 {
            txt.print("bit 6 not set\n")
        } else {
            txt.print("bit 6 set\n")
        }
        if variable & %01000000 != 0 {
            txt.print("bit 6 set\n")
        } else {
            txt.print("bit 6 not set\n")
        }
    }
}
