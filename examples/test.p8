%import textio
%zeropage basicsafe

main {
    sub start() {

        ubyte[5] xx = [11,22,33,44,55]
        ubyte[5] yy = [101,102,103,104,105]
        ubyte i=3
        ubyte j = 4
        uword screen

        ubyte result = xx[i] + yy[j]        ; TODO optimize to use add addr,y
        txt.print_ub(result)    ; 149
        txt.nl()
        result = xx[i] + yy[i]              ; TODO optimize to use add addr,y
        txt.print_ub(result)    ; 148
        txt.nl()
        @(screen+i) = xx[i] + yy[i]     ; TODO why is this using P8ZP_SCRATCH_B1?

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
