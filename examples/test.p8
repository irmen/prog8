%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        ubyte [] array = 100 to 110

        for cx16.r0L in array {
            txt.print_ub(cx16.r0L)
            txt.spc()
        }
        txt.nl()

        ubyte x = 14
        if x in 10 to 20 {
            txt.print("yep1\n")
        }
        if x in 20 to 30 {
            txt.print("yep2\n")
        }

        if x in 10 to 20 step 2 {
            txt.print("yep1b\n")
        }
        if x in 20 to 30 step 2 {
            txt.print("yep2b\n")
        }

        if x in 20 to 10 step -2 {
            txt.print("yep1c\n")
        }
        if x in 30 to 20 step -2 {
            txt.print("yep2c\n")
        }

        txt.nl()

        ubyte @shared y = 12
        if y in 10 to 20 {
            txt.print("yep1\n")
        }
        if y in 20 to 30 {
            txt.print("yep2\n")
        }

        if y in 20 downto 10 {
            txt.print("yep1c\n")
        }
        if y in 30 downto 20 {
            txt.print("yep2c\n")
        }
    }
}
