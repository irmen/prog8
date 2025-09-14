%import textio
%zeropage basicsafe

main {
    struct Node {
        ubyte id
        str name
        uword array
    }

    sub start() {
        ^^Node[] nodes = [
            ^^Node:[1,"one", 1000 ],
            ^^Node:[2,"two", 2000 ],
            ^^Node:[3,"three", 3000],
            ^^Node:[],
            ^^Node:[],
            ^^Node:[],
        ]

        for cx16.r0 in nodes {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()
    }
}
