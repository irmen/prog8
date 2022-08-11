%import textio
%zeropage basicsafe

main {
    sub start() {
        uword ww = $ff34
        ww = ww ^ ww<<8
        txt.print_uwhex(ww, true)
        ww = $ff34
        ww = ww ^ mkword(lsb(ww), 0)
        txt.print_uwhex(ww, true)
        ww = $ff34
        ww  = ww ^ ww >> 8
        txt.print_uwhex(ww, true)
        ww = $ff34
        ww  = ww ^ msb(ww)
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
