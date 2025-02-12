%import textio
%zeropage basicsafe

main {

    sub start() {
        const uword x=128

        if cx16.r0L==x
            return
        if cx16.r0L>x
            return
        if cx16.r0L<x
            return
    }
}
