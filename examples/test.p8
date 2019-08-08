%import c64utils
%import c64lib
%zeropage dontuse

main {

    sub start() {

        for ubyte ub1 in 10 to 20 {
            for ubyte ub2 in ub1 to 30 {
                c64scr.print_ub(ub2)
                c64.CHROUT(',')
            }
            c64.CHROUT('\n')
        }
    }
}
