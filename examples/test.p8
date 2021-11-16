%import textio
%import floats

main {

    sub start() {

        ubyte xx = 1.234
        ubyte yy = 2.234
        uword aw
        byte bb
        float fl

        ; TODO:  bitwise operations with a negative constant number -> replace the number by its positive 2 complement

        if aw<1
            yy++
        if aw<=0
            yy++

        if yy<1
            yy++
        if yy<=0
            yy++
        if bb<1
            yy++
        if bb<=0
            yy++

        txt.print_ub(yy)
        txt.print_uw(aw)

    }
}
