%import lists
%import textio
%zeropage basicsafe

main {
    struct MyNode {
        ^^MyNode Succ       ; must be first field (offset 0)
        ^^MyNode Pred       ; must be second field
        ubyte    value      ; your own payload follows
    }
    struct MyList {
        ^^MyNode Head
        pointer  Tail
        ^^MyNode TailPred
    }

    sub start() {
        ^^MyList mylist = []                ; zero-initialized header
        ^^MyNode n1 = [0, 0, 42]            ; Succ, Pred, value
        ^^MyNode n2 = [0, 0, 99]
        ^^MyNode n3 = [0, 0, 123]
        lists.init(mylist)
        lists.add_tail(mylist, n1)
        lists.add_tail(mylist, n2)
        lists.add_tail(mylist, n3)
        ^^MyNode cursor = mylist.Head
        while cursor != &mylist.Tail {
            txt.print_ub(cursor.value)
            txt.nl()
            cursor = cursor.Succ
        }
    }
}
