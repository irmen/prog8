%import textio
%zeropage dontuse

main {
    sub start() {
        const ubyte CONSTANT=80
        cx16.r0 = 0
        unroll CONSTANT-10 {
            cx16.r0++
        }
        txt.print_uw(cx16.r0)


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
