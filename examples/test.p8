%import textio
%zeropage basicsafe

main {
    sub start() {
        struct Node {
            ^^Node next
            uword data
        }

        ^^Node n = 4000
        &^^Node n2 = 5000

        n.data=9999
        txt.print_uw(n.data)
        n2.data=9999
        txt.print_uw(n2.data)
    }
}
