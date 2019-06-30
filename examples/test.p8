%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {

        word w1 = 1111
        word w2 = 2222
        ubyte b1 = 11
        ubyte b2 = 22
        ubyte[] arr1 = [1,2,3,4]
        ubyte[] arr2 = [1,2,3,4]
        A=99
        Y=88

        c64scr.print_w(w1)
        c64.CHROUT(',')
        c64scr.print_w(w2)
        c64.CHROUT('\n')
        swap(w1, w2)
        c64scr.print_w(w1)
        c64.CHROUT(',')
        c64scr.print_w(w2)
        c64.CHROUT('\n')

        c64scr.print_ub(A)
        c64.CHROUT(',')
        c64scr.print_ub(Y)
        c64.CHROUT('\n')
        swap(A, Y)
        c64scr.print_ub(A)
        c64.CHROUT(',')
        c64scr.print_ub(Y)
        c64.CHROUT('\n')

        c64scr.print_ub(arr1[2])
        c64.CHROUT(',')
        c64scr.print_ub(arr2[3])
        c64.CHROUT('\n')
        swap(arr1[2], arr2[3])
        c64scr.print_ub(arr1[2])
        c64.CHROUT(',')
        c64scr.print_ub(arr2[3])
        c64.CHROUT('\n')

        c64scr.print_ub(A)
        c64.CHROUT(',')
        c64scr.print_ub(b1)
        c64.CHROUT('\n')
        swap(A, b1)
        c64scr.print_ub(A)
        c64.CHROUT(',')
        c64scr.print_ub(b1)
        c64.CHROUT('\n')

        c64scr.print_ub(b2)
        c64.CHROUT(',')
        c64scr.print_ub(Y)
        c64.CHROUT('\n')
        swap(b2, Y)
        c64scr.print_ub(b2)
        c64.CHROUT(',')
        c64scr.print_ub(Y)
        c64.CHROUT('\n')

        c64scr.print_ub(arr1[2])
        c64.CHROUT(',')
        c64scr.print_ub(Y)
        c64.CHROUT('\n')
        swap(arr1[2], Y)
        c64scr.print_ub(arr1[2])
        c64.CHROUT(',')
        c64scr.print_ub(Y)
        c64.CHROUT('\n')

        c64scr.print_ub(Y)
        c64.CHROUT(',')
        c64scr.print_ub(arr2[3])
        c64.CHROUT('\n')
        swap(Y, arr2[3])
        c64scr.print_ub(Y)
        c64.CHROUT(',')
        c64scr.print_ub(arr2[3])
        c64.CHROUT('\n')

    }
}
