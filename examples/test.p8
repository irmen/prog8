%import textio
%zeropage basicsafe

main {
    sub start() {
        long total

        for iw in 1000 to 2000 {
            total += iw
        }
        txt.print_l(total)
        txt.nl()


        for iw in 2000 downto 1000 {
            total += iw
        }
        txt.print_l(total)
        txt.nl()

        for i in 88888 to 99999 {
            total += i
        }
        txt.print_l(total)
        txt.nl()

        for i in 88888 downto 77777 {
            total -= i
        }
        txt.print_l(total)
        txt.nl()
    }
}
