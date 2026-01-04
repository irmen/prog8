%import textio
%zeropage basicsafe

main {
    sub start() {
        when cx16.r0L {
            12 -> cx16.r0++
            else -> cx16.r0--
        }

        if cx16.r0L==12
            cx16.r0++
        else
            cx16.r0--
    }
}
