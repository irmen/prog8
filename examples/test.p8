%import textio

main {

    sub start() {
;        uword[] routines = [ 0, &command, $4444 ]
;        cx16.r0 = routines[1]

        &ubyte[5] cells = $4000
        cells = [1,2,3,4,5]
        str name = "irmen"

        ubyte[5] othercells = [11,22,33,44,55]

        txt.print_ub(1 in cells)
        txt.print_ub(44 in othercells)
        txt.print_ub('a' in name)
        txt.print_ub('e' in name)
    }

    sub command() {
        cx16.r0++
    }
}
