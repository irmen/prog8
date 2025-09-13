%import textio
%zeropage basicsafe

main {
    struct Node {
        ubyte id
        str name
        uword array
    }

    ^^Node @shared @zp node = 2000

    sub start() {
        ^^Node[] nodes = [
            ^^Node:[1,"one", 1000 ],
            ^^Node:[2,"two", 2000 ],
            ^^Node:[3,"three", 3000]
        ]
        txt.print_uw(nodes[0])
        txt.spc()
        txt.print_uw(nodes[1])
        txt.spc()
        txt.print_uw(nodes[2])
        txt.nl()
    }
}
