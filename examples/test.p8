%import textio
%zeropage basicsafe

main {
    sub start() {
        const uword one = 1
        const uword two = 2
        uword @shared answer = one * two >> 8
        txt.print_uw(answer)
        txt.spc()
        txt.print_uw(one * two >> 8)
        txt.nl()

        const uword uw1 = 99
        const uword uw2 = 22
        uword @shared answer2 = uw1 * uw2 >> 8
        txt.print_uw(answer2)
        txt.spc()
        txt.print_uw(uw1 * uw2 >> 8)
        txt.nl()

        uword @shared uw3 = 99
        uword @shared uw4 = 22
        uword @shared answer3 = uw3 * uw4 >> 8
        txt.print_uw(answer3)
        txt.spc()
        txt.print_uw(uw3 * uw4 >> 8)
        txt.nl()
    }
}
