%import textio
%zeropage basicsafe

main {
    sub start() {
        uword @shared qq = $2ff33
        cx16.r0 = $1fc0f
    }
}
