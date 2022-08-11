%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte left = $12
        ubyte right = $34
        uword ww

        ww = mkword(left, right)
        txt.print_uwhex(ww, true)
        ww = mkword(left, 0)
        txt.print_uwhex(ww, true)
        ww = mkword(0, right)
        txt.print_uwhex(ww, true)
        ww = right as uword
        txt.print_uwhex(ww, true)
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
