%import textio
%zeropage basicsafe

main {
    romsub $FFD2 = chrout(ubyte ch @ A)
    sub start() {
        ubyte ch = '2'
        chrout(ch)
    }
}
