%import textio
%zeropage basicsafe
%option no_sysinit

main {
    ; Test the routine
    sub start() {
        long counter
        long lv
        for lv in 4000 to 94000-1 {
            counter++
        }
        for lv in 94000-1 downto 4000 {
            counter++
        }
        txt.print_l(counter)
        txt.nl()
    }
}

