%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print("expected: 0 10 30\n")
        counter = 0
        txt.print_ub(nodefer(10))
        txt.spc()
        txt.print_ub(nodefer(20))
        txt.spc()
        txt.print_ub(nodefer(30))
        txt.nl()

        counter = 0
        txt.print_ub(add(10))
        txt.spc()
        txt.print_ub(add(20))
        txt.spc()
        txt.print_ub(add(30))
        txt.nl()

        counter = 0
        txt.print_ub(add2(10))
        txt.spc()
        txt.print_ub(add2(20))
        txt.spc()
        txt.print_ub(add2(30))
        txt.nl()
    }

    ubyte counter = 0

    sub nodefer(ubyte amount) -> ubyte {
        ubyte result = counter
        counter += amount
        return result
    }

    sub add(ubyte amount) -> ubyte {
        defer counter += amount         ; TODO FIX : BORKED!
        return counter
    }

    sub add2(ubyte amount) -> ubyte {
        cx16.r0L = 0
        defer counter += amount
        return counter + cx16.r0L
    }
}
