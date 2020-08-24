%import c64utils
%zeropage basicsafe

main {

    sub start() {
        uword lines = 1
        uword score = $1000

        ubyte x
        ubyte y

        lines = mkword(x, y)
        c64scr.print_uw(lines)
        c64.CHROUT('\n')
        c64scr.print_uw(score)
        c64.CHROUT('\n')
    }
}
