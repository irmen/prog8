%import textio
%zeropage basicsafe

main {
    sub start() {

        ubyte @shared xx = @(cx16.r5)
        xx++

    }
}
