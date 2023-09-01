%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte index = 100
        ubyte[] t_index = [1,2,3,4,5]
        ubyte nibble = 0

        index += t_index[4]
        index += t_index[nibble]
        nibble++
        index -= t_index[3]
        index -= t_index[nibble]
        txt.print_ub(index)     ; 100
    }
}
