%import textio

%zeropage basicsafe

main {
    sub start() {
        uword active_world = memory("world", 80*50, 0)
        uword cell_off = 500
        const uword STRIDE = 40
        sys.memset(active_world, 80*50, 1)
        txt.print_ub(count())       ; TODO prints 1, must be 8

        sub count() -> ubyte {
            return active_world[cell_off-STRIDE-1] + active_world[cell_off-STRIDE] + active_world[cell_off-STRIDE+1] +
                   active_world[cell_off-1] + active_world[cell_off+1] +
                   active_world[cell_off+STRIDE-1] + active_world[cell_off+STRIDE] + active_world[cell_off+STRIDE+1]
        }
    }
}
