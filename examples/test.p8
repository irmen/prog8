%import textio

%zeropage basicsafe

main {
    sub start() {
        ubyte arg3 = 200
        uword result = calc(101, 202, arg3)
        txt.print_uw(result)

        str name = "irmen"
        ubyte[] array = [1,2,3,4]
        bool xx = 44 in array
        bool yy = 'a' in name
    }

    sub calc(ubyte a1, ubyte a2, ubyte a3) -> uword {
        return a1 as uword + a2 + a3
    }
}
