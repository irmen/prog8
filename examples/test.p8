%import textio
%zeropage basicsafe

main {
    ; TODO why allocated not cleaned????
    ubyte[255] @shared @requirezp arr1
    uword[100] @shared @requirezp arr2
    sub start() {
;        txt.print_ub(arr1[3])
;        txt.spc()
;        txt.print_uw(arr2[1])
;        txt.spc()
    }
}
