%import textio
%zeropage basicsafe

main {
    sub start() {
        uword crc = $ffff
        txt.print_uwhex(crc | (crc & $8000), true)
;        if crc & $8000          ;  msb(crc) & $80
;            txt.print("yes")
;        else
;            txt.print("fail!")
;
;        if msb(crc) & $80
;            txt.print("yes")
;        else
;            txt.print("fail!")
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
