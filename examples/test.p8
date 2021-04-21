%import textio
%import string
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {

    sub start() {
        ubyte fromub = 120
        uword fromuw = 400
        byte fromb = 120
        word fromw = 400

        uword counter
        byte bc
        ubyte ubc
        word wc
        uword uwc

        counter = 0
        for bc in fromb to 0 step -1 {
            counter++
            txt.print_b(bc)
            txt.spc()
        }
        txt.nl()
        txt.print_uw(counter)
        txt.nl()
        txt.nl()

        counter = 0
        for ubc in fromub to 0 step -1 {
            counter++
            txt.print_ub(ubc)
            txt.spc()
        }
        txt.nl()
        txt.print_uw(counter)
        txt.nl()
        txt.nl()

        counter = 0
        for wc in fromw to 0 step -1 {
            counter++
            txt.print_w(wc)
            txt.spc()
        }
        txt.nl()
        txt.print_uw(counter)
        txt.nl()
        txt.nl()

        ; TODO FIX THIS LOOP??
        counter = 0
        for uwc in fromuw to 0 step -1 {
            counter++
            txt.print_uw(uwc)
            txt.spc()
        }
        txt.nl()
        txt.print_uw(counter)
        txt.nl()
        txt.nl()
    }

}

