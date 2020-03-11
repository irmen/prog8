%zeropage basicsafe

main {

    sub start() {
        ubyte ub
        for ub in 12 to 3 step -1 {
            c64scr.print_ub(ub)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for ub in 12 to 3 step -4 {
            c64scr.print_ub(ub)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for ub in 12 downto 3 {
            c64scr.print_ub(ub)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for ub in 12 downto 3 step -4 {
            c64scr.print_ub(ub)
            c64scr.print(",")
        }
        c64scr.print("\n")
    }
}
