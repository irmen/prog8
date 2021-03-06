%import textio
%zeropage basicsafe


main {
    sub start() {
        ubyte[] arr1 = [1,2,3]
        ubyte[] arr2 = [9,9,9]

        arr1[2]=42
        txt.print_ub(arr2[0])
        txt.chrout(',')
        txt.print_ub(arr2[1])
        txt.chrout(',')
        txt.print_ub(arr2[2])
        txt.nl()
        arr2=[99,88,77]
        txt.print_ub(arr2[0])
        txt.chrout(',')
        txt.print_ub(arr2[1])
        txt.chrout(',')
        txt.print_ub(arr2[2])
        txt.nl()
        arr2=arr1
        txt.print_ub(arr2[0])
        txt.chrout(',')
        txt.print_ub(arr2[1])
        txt.chrout(',')
        txt.print_ub(arr2[2])
        txt.nl()
        txt.nl()



        struct MyType {
            uword v1
            uword w1
            uword w2
        }

        MyType m1 = [1, 888, 999]
        MyType m2 = [22, 222, 222]

        txt.print_uw(m2.v1)
        txt.chrout(',')
        txt.print_uw(m2.w1)
        txt.chrout(',')
        txt.print_uw(m2.w2)
        txt.nl()
        m2 = [111,222,333]
        txt.print_uw(m2.v1)
        txt.chrout(',')
        txt.print_uw(m2.w1)
        txt.chrout(',')
        txt.print_uw(m2.w2)
        txt.nl()
        m2 = m1
        txt.print_uw(m2.v1)
        txt.chrout(',')
        txt.print_uw(m2.w1)
        txt.chrout(',')
        txt.print_uw(m2.w2)
        txt.nl()
    }
}
