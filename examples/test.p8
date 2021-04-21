%import textio
%import string
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {

    sub start() {
        ubyte num_entries

        num_entries = 10
        ubyte ix
        for ix in (num_entries-1)*2 downto 0 step -2 {
            txt.print_ub(ix)
            txt.spc()
        }

        txt.nl()
        num_entries = 10
        for ix in num_entries-1 downto 0 {
            txt.print_ub(ix*2)
            txt.spc()
        }
    }

}

