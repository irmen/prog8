%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword  n=0
        uword i
        txt.print("ascending:\n")
        n=10
        for i in 0 to n step 3 {
            cx16.r0++
            txt.print(" i=")
            txt.print_uw(i)
            txt.spc()
            txt.print(" n=")
            txt.print_uw(n)
            txt.nl()
        }

        txt.print("descending:\n")
        n=0
        for i in 10 downto n step -3 {
            cx16.r0++
            txt.print(" i=")
            txt.print_uw(i)
            txt.spc()
            txt.print(" n=")
            txt.print_uw(n)
            txt.nl()
        }
    }
}

