%import textio
%zeropage basicsafe


main {
    uword[100] @nosplit array1 = 1 to 100
    uword[100] @split array2 = 100 downto 1

    sub start() {
        for cx16.r2 in array1 {
            txt.print_uw(cx16.r2)
            txt.spc()
        }
        txt.nl()
        txt.nl()
        for cx16.r2 in array2 {
            txt.print_uw(cx16.r2)
            txt.spc()
        }
        txt.nl()
        txt.nl()
    }
}
