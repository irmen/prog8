%import textio
%import math
%zeropage basicsafe

main {
    sub start() {
        ubyte index
        ubyte[] t_index = [1,2,3,4,5]
        ubyte nibble = 0

        index -= t_index[4]
        index -= t_index[nibble]
    }
}
