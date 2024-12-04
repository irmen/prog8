%import textio
%zeropage basicsafe

main {
    sub start() {
        if (cx16.r0 & 1) as bool == false
            cx16.r1++
        else
            cx16.r2++

        if (cx16.r0 & 1) as bool == true
            cx16.r1++
        else
            cx16.r2++

        if not((cx16.r0 & 1) as bool)
            cx16.r1++
        else
            cx16.r2++

        if (cx16.r0 & 1) as bool
            cx16.r1++
        else
            cx16.r2++
    }
}
