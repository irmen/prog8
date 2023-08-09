%import textio
%zeropage basicsafe

main {
    sub start () {
        ubyte[4] array = 10 to 13
        ubyte[4] array2 = 10 to 20 step 3
        ubyte[4] array3 = 20 downto 10 step 3       ; TODO fix error about empty range expression

        ubyte xx
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

        byte bb
        for bb in 10 downto -10 step -3 {        ; TODO fix IR/VM code that wraps around instead of stopping at -8
            txt.print_b(bb)
            txt.spc()
        }
    }
}
