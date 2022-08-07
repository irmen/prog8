%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte[] arr = [1,2,3,4]
        uword pointer
        ubyte ix

        arr[ix] = arr[ix]+1

;        arr[3] = arr[3]+1
;        pointer[3] = pointer[3]+1

        txt.print_ub(arr[3])
    }
}
