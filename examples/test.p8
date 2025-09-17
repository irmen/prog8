%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print_uw(allocator.alloc(10))
        txt.spc()
        txt.print_uw(allocator.alloc(20))
        txt.spc()
        txt.print_uw(allocator.alloc(30))
        txt.nl()

        allocator.freeall()

        txt.print_uw(allocator.alloc(10))
        txt.spc()
        txt.print_uw(allocator.alloc(20))
        txt.spc()
        txt.print_uw(allocator.alloc(30))
        txt.nl()
    }
}


    allocator {
        ; extremely trivial allocator allocator
        uword buffer = memory("allocator", 2000, 0)
        uword next = buffer

        sub alloc(ubyte size) -> uword {
            defer next += size
            return next
        }

        sub freeall() {
            ; cannot free individual allocations only the whole allocator at once
            next = buffer
        }
    }
