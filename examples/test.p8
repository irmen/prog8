%import textio
%zeropage basicsafe

main {
    sub start() {
        bool b1 = false
        bool b2 = true
        bool b3

        b2 = b2 and b1
        txt.print_ub(b2)
        txt.nl()

        ubyte ub1 = 1
        ubyte ub2 = 2
        ubyte ub3

        ub2 = ub2 + ub1
        txt.print_ub(ub2)
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
