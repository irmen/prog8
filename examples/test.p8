%import lists
%import textio
%zeropage basicsafe

main {
    sub start() {
        reverseiter()
        listiter()
    }

    sub reverseiter() {
        for x in "hello" {
            txt.chrout(x)
        }

        txt.nl()

        for x in "hello2" step -1 {
            txt.chrout(x)
        }
        txt.nl()

        for y in [11,22,33,44] {
            txt.print_ub(y)
            txt.spc()
        }
        txt.nl()

        for y in [11,22,33,44] step -1 {
            txt.print_ub(y)
            txt.spc()
        }
        txt.nl()
    }

    struct MyNode {
        ^^MyNode Succ
        ^^MyNode Pred
        ubyte value
    }

    struct MyList {
        ^^MyNode Head
        pointer Tail
        ^^MyNode TailPred
    }

    sub listiter() {
        ^^MyList mylist = []
        ^^MyNode n1 = [0,0, 11]
        ^^MyNode n2 = [0,0, 22]
        ^^MyNode n3 = [0,0, 33]
        lists.init(mylist)
        lists.add_tail(mylist, n1)
        lists.add_tail(mylist, n2)
        lists.add_tail(mylist, n3)

        for node in mylist {
            txt.print_ulhex(node as long, true)
            txt.spc()
            txt.print_ub(node.value)
            txt.nl()
        }
    }
}
