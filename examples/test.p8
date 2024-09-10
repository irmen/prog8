%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        ubyte x
        uword w
        uword @shared wstart=1000
        ubyte @shared bstart=100
        uword y
        uword duration
        byte b


        cbm.SETTIM(0,0,0)
        repeat 1000 {
            y=0
            for w in wstart downto 0 {
                y++
            }
            for w in wstart downto 1 {
                y++
            }
            ; TODO words
        }
        txt.print_uw(cbm.RDTIM16())
        if y!=2001
            txt.print("error\n")

        ; without new loops: $26e, 482 jiffies

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
