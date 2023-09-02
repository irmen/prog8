%import textio
%zeropage basicsafe

main {
    sub start() {

        uword ww= 300
        @($4000) = 100
        ww -= @($4000)
        ww -= 100
        txt.print_uw(ww)        ; 100

;        ubyte index = 100
;        ubyte[] t_index = [1,2,3,4,5]
;        ubyte nibble = 0
;
;        index = index + t_index[4]
;        index = index + t_index[nibble]
;        txt.print_ub(index)     ; 106
;        txt.nl()
;
;        nibble++
;        index = index - t_index[3]
;        index = index - t_index[nibble]
;        txt.print_ub(index)     ; 100
;        txt.nl()
    }
}
