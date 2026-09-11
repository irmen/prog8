%import textio
%zeropage basicsafe

main {
    sub start() {

        for cx16.r0L in 5 downto 0 {
            txt.print_ub(cx16.r0L)
            txt.spc()
        }
    }
}
