%import textio
%zeropage basicsafe

main {
    uword[]  array = [1000, 2000, 9000, 8000, 5000]
    sub start() {
        txt.print_bool(1000 in array)
        txt.spc()
        txt.print_bool(9000 in array)
        txt.spc()
        txt.print_bool(5000 in array)
        txt.spc()
        txt.print_bool(9001 in array)
    }
}
