%import textio
%zeropage basicsafe

main {
    ubyte @zp mainglobal1=10

    uword [2] nullwords
    ubyte [2] nullbytes
    uword [2] valuewords = [1111,22222]
    ubyte [2] valuebytes = [111,222]
    str name = "irmen"

    uword [2] @requirezp zpnullwords
    ubyte [2] @requirezp zpnullbytes
    uword [2] @requirezp zpvaluewords = [11111,22222]
    ubyte [2] @requirezp zpvaluebytes = [111,222]
    str @requirezp zpname = "irmenzp"

    sub start() {
        prog8_lib.P8ZP_SCRATCH_B1 = 1

        txt.print_uw(nullwords[1])
        txt.nl()
        txt.print_ub(nullbytes[1])
        txt.nl()
        txt.print_uw(valuewords[1])
        txt.nl()
        txt.print_ub(valuebytes[1])
        txt.nl()
        txt.print(name)
        txt.nl()
        txt.nl()

        txt.print_uw(zpnullwords[1])
        txt.nl()
        txt.print_ub(zpnullbytes[1])
        txt.nl()
        txt.print_uw(zpvaluewords[1])
        txt.nl()
        txt.print_ub(zpvaluebytes[1])
        txt.nl()
        txt.print(zpname)
        txt.nl()

    }
}
