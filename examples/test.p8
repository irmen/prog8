%import textio
%zeropage basicsafe

main {
    sub start() {
        byte index = 100
        byte[] t_index = [-1,-2,-3,-4,-5]

        index = 4
        index = index>t_index[4]
        txt.print_b(index)
        txt.nl()
        index = index>t_index[4]
        txt.print_b(index)
        txt.nl()

;        index = index < t_index[4]
;        index = index < t_index[nibble]
;        txt.print_ub(index)
;        txt.nl()
;
;        nibble++
;        index = index > t_index[3]
;        index = index > t_index[nibble]
;        txt.print_ub(index)     ; 100
;        txt.nl()
    }
}
