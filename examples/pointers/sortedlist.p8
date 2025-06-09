; single linked list, that keeps the elements in ascending sorted order
; TODO work in progress


%import textio

main {
    sub start() {
        printlist()
    }

    sub printlist() {
        ubyte count = 0
        ^^slist.Node n = slist.head
        while n {
            txt.print_uw(n.size)
            txt.chrout(':')
            txt.print_ub(n.value)
            txt.print(", ")
            count++
            n = n.next
        }
        txt.print("\nlength=")
        txt.print_ub(count)
        txt.nl()
    }
}

slist {
    struct Node {
        ^^Node next
        uword size
        ubyte value
    }

    ^^Node head = 0

    sub add(^^Node node) {
        node.next = head
        head = node
    }
}


arena {
    ; extremely trivial arena allocator (that never frees)
    uword buffer = memory("arena", 2000, 0)
    uword next = buffer

    sub alloc(ubyte size) -> uword {
        defer next += size
        return next
    }
}
