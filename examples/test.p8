%zeropage basicsafe

main {
    sub start() {
        uword @shared curr_sequence
        ubyte @shared  sequence_curr_step

        uword @shared sequence_offset = &curr_sequence[sequence_curr_step]
    }
}
