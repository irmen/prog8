%import textio

main {

    sub start() {

        ubyte xx = 1.234
        ubyte yy = 2.234
        uword aw

        ; TODO:  bitwise operations with a negative constant number -> replace the number by its positive 2 complement

        aw = xx ^ 65535
        aw = ~xx
        yy = xx ^ 255
        yy = ~xx

;    *(&X)  =>  X
;    X % 1  => 0
;    X / 1  =>  X
;    X ^ -1  =>  ~x
;    X >= 1  =>  X > 0
;    X <  1  =>  X <= 0

        txt.print_ub(yy)
        txt.print_uw(aw)

    }
}
