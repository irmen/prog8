%import textio
%zeropage basicsafe

main {
    sub start() {
        long total
        cx16.r1 = 2000
        cx16.r2 = 1000

        for cx16.r0 in 1000 to cx16.r1 {
            total += cx16.r0
        }
        txt.print_l(total)
        txt.nl()


        for cx16.r0 in 2000 downto cx16.r2 {
            total += cx16.r0
        }
        txt.print_l(total)
        txt.nl()
    }
}
