%import textio

main {

    sub start() {
;        uword[] routines = [ 0, &command, $4444 ]
;        cx16.r0 = routines[1]

        &ubyte[5] cells = $4000
        cells = [1,2,3,4,5]
        ubyte ub
        for ub in cells {
            txt.print_ub(ub)
            txt.spc()
        }
    }

    sub command() {
        cx16.r0++
    }
}
