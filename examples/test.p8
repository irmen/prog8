%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte[] buffer = [$11,$22,$33,$44]
        uword data = &buffer

        uword crc = $ffff
        crc ^= mkword(@(data), 0)
        txt.print_uwhex(crc, true)
        crc = $ffff
        ubyte variable = @(data)
        crc ^= mkword(variable, 0)
        txt.print_uwhex(crc, true)
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
