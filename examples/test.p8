%import textio
%import math
%zeropage basicsafe

main {
    sub start() {
        str name = "irmen"
        ubyte[] bytes = [1,2,3]
        uword[] words = [1,2,3,4,5]
        txt.print_ub('z' in name)
        txt.print_ub('r' in name)
        txt.print_ub('r' in "derp")
        txt.print_ub(4 in bytes)
        txt.print_ub($0004 in words)
    }
}
