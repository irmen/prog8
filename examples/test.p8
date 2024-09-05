%import textio

%zeropage basicsafe

main {
    sub start() {
        for cx16.r0L in 5 to 5 {
            txt.print("derp0.")
            txt.print_ub(cx16.r0L)
            txt.nl()
        }
        for cx16.r0L in 100 downto 100 {
            txt.print("derp1.")
            txt.print_ub(cx16.r0L)
            txt.nl()
        }
        for cx16.r0L in 100 to 100 {
            txt.print("derp2.")
            txt.print_ub(cx16.r0L)
            txt.nl()
        }
        for cx16.r0 in 2222 downto 2222 {
            txt.print("derp3.")
            txt.print_uw(cx16.r0)
            txt.nl()
        }
        for cx16.r0 in 2222 to 2222 {
            txt.print("derp4.")
            txt.print_uw(cx16.r0)
            txt.nl()
        }
        for cx16.r0L in 100 downto 100 step -5 {
            txt.print("derp5.")
            txt.print_ub(cx16.r0L)
            txt.nl()
        }
        for cx16.r0L in 100 to 100 step 5 {
            txt.print("derp6.")
            txt.print_ub(cx16.r0L)
            txt.nl()
        }
        for cx16.r0 in 2222 downto 2222 step -5 {
            txt.print("derp7.")
            txt.print_uw(cx16.r0)
            txt.nl()
        }
        for cx16.r0 in 2222 to 2222 step 5 {
            txt.print("derp8.")
            txt.print_uw(cx16.r0)
            txt.nl()
        }
    }
}
