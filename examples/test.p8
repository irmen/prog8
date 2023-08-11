%import textio
%zeropage basicsafe

main {
    sub start () {
        ubyte[4] @shared array = 10 to 20 step 3
        ubyte[4] @shared array2 = 20 downto 10 step -3
        byte[7] @shared array3 = 10 downto -10 step -3

        ubyte xx
        byte bb

        cx16.r1=0
        for cx16.r0 in 0 to 10 {
            cx16.r1++
        }
        txt.print_uw(cx16.r1)
        txt.nl()
        txt.nl()

        for xx in array {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()
        for xx in array2 {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()
        for bb in array3 {
            txt.print_b(bb)
            txt.spc()
        }
        txt.nl()
        txt.nl()


        for xx in 10 to 20 step 3 {             ; TODO fix IR/VM code that wraps around instead of stopping at 19
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()

        for xx in 20 downto 10 step -3 {        ; TODO fix IR/VM code that wraps around instead of stopping at 11
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()

        for bb in 10 downto -10 step -3 {        ; TODO fix IR/VM code that wraps around instead of stopping at -8
            txt.print_b(bb)
            txt.spc()
        }

        txt.nl()
        txt.nl()
        ubyte ending = 20

        for xx in 10 to ending step 3 {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()
        ending =10
        for xx in 20 downto ending step -3 {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()

        byte endingb = -10
        for bb in 10 downto endingb step -3 {
            txt.print_b(bb)
            txt.spc()
        }
    }
}
