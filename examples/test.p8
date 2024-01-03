%import textio
%zeropage basicsafe

main {
    sub start() {

        cx16.r0 = 500
        if cx16.r0 in 127 to 5555
            cx16.r0++


        cx16.r0 = 50
        if cx16.r0 in 5555 downto 127
            cx16.r0++

    }
}
