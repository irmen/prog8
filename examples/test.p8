%import textio
%zeropage basicsafe
%option no_sysinit

main {
    struct NodeStr {
        ubyte a
        bool flag
        str name
    }

    struct NodeNostr {
        ubyte a
        bool flag
        ubyte[5] array
        word number
    }

    sub start() {
        ^^NodeNostr k3 = [1, false, [65,66,67,68,0], 9999]

        ; the following must print:  1 false ABCD 9999
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
