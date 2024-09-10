%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        ubyte x
        uword w
        uword @shared wstart=50000
        ubyte @shared bstart=127
        uword y
        uword duration
        byte b


        cbm.SETTIM(0,0,0)
        repeat 5000 {
            y=0
;            for x in bstart downto 0 {
;                y++
;            }
            x = bstart
            do {
                y++
                x--
            } until x==255
        }
        txt.print_uw(cbm.RDTIM16())
        if y!=128
            txt.print("error 1\n")

/*
        for w in 65535 downto 0 {
            y++
        }
        if y!=0
            txt.print("error 10\n")

        y=0
        for w in 0 to 65535 {
            y++
        }
        if y!=0
            txt.print("error 11\n")
*/

        txt.print("\nall done\n")
    }
}
