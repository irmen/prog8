%import textio

%zeropage basicsafe

main {
    sub start() {
        uword active_world = memory("world", 80*50, 0)
        uword @shared cell_off = 500
        uword @shared cell_off_1 = cell_off+1
        const uword STRIDE = 40
        sys.memset(active_world, 80*50, 1)

        txt.print_ub(active_world[500] + active_world[501])  ; TODO prints 1, must be 2
        txt.nl()
        txt.print_ub(active_world[cell_off] + active_world[cell_off_1])  ; TODO prints 1, must be 2
        txt.nl()
        txt.print_ub(count())       ; TODO prints 1, must be 8
        txt.nl()

        sub count() -> ubyte {
            return active_world[cell_off-STRIDE-1] + active_world[cell_off-STRIDE] + active_world[cell_off-STRIDE+1] +
                   active_world[cell_off-1] + active_world[cell_off+1] +
                   active_world[cell_off+STRIDE-1] + active_world[cell_off+STRIDE] + active_world[cell_off+STRIDE+1]
        }
    }
}
