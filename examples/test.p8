%import textio
%zeropage basicsafe

main {
    sub start() {
        uword @shared z = 100
        ubyte @shared x = 200

        for x in 15 downto 1 {
            txt.print_uw(x*$0002-z)     ; TODO fix 6502 optimization bug
            txt.nl()
        }
    }
}
