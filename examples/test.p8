%import textio
%zeropage basicsafe

main {
    sub start() {
        ^^bool flags
        ^^word words
        ^^long longs


        ; TODO optimized translation of the peekX and pokeX calls:
        if flags[cx16.r0]
            flags[cx16.r0] = true
        if words[cx16.r0]!=0
            words[cx16.r0] = 9990
        if longs[cx16.r0]!=0
            longs[cx16.r0] = 999999
    }
}
