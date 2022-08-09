%import textio
%import diskio
%zeropage basicsafe

main {
    sub start() {
        romsub $5000 = external_command() -> ubyte @R15
        ubyte @shared result = external_command()
    }

;    sub start2() {
;        ubyte[] arr = [1,2,3,4]
;        uword pointer
;        ubyte ix
;
;        arr[ix] = arr[ix]+1
;
;;        arr[3] = arr[3]+1
;;        pointer[3] = pointer[3]+1
;
;        txt.print_ub(arr[3])
;    }
}
