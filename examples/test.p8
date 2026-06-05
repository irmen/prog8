%encoding iso
%import textio
%zeropage basicsafe

main {
    struct Node {
        ubyte a
        bool flag
        ubyte[5] array
        word number
    }

    sub start() {
        ^^Node k2 = [42, false, [65,66,67,68,0], 9999]
        ^^Node k3 = $4000
        k3^^=k2^^

        ; the following must print:  42 false ABCD 9999
        txt.print_ub(k3.a)
        txt.spc()
        txt.print_bool(k3.flag)
        txt.spc()
        txt.print(k3.array)
        txt.spc()
        txt.print_w(k3.number)
        txt.nl()
    }
}
