%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8

    ; @todo gradle fatJar should include the antlr runtime jar

    sub start() {

    for ubyte @zp fastindex in 10 to 20 {
        c64scr.print_ub(fastindex)
        c64.CHROUT('\n')
        ; do something
    }

    }
}
