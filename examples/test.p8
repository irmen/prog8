%import textio
%zeropage basicsafe

main {

    sub start() {

        ubyte[6] array = [ 1,2,3,
; Comment here
                           4,5,6 ]

        txt.print_ub(len(array))
    }
}
