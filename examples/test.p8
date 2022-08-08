%import textio
%zeropage basicsafe

main {
    sub start() {
        str @zp zpstr = "irmen"
        ubyte[3] @zp zparr = [1,2,3]

        txt.print(zpstr)
        txt.print_ub(zparr[2])
    }


    sub start2() {
        ubyte[] arr = [1,2,3,4]
        uword pointer
        ubyte ix

        arr[ix] = arr[ix]+1

;        arr[3] = arr[3]+1
;        pointer[3] = pointer[3]+1

        txt.print_ub(arr[3])
    }
}
