%import textio
%zeropage basicsafe

main {
    sub start() {

        uword array = $8000
        array[0] = 10
        array[1] = 20
        array[2] = 30

        txt.print_ub(@($8000))
        txt.spc()
        txt.print_ub(@($8001))
        txt.spc()
        txt.print_ub(@($8002))
        txt.spc()
    }
}
