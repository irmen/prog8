; single linked list, that keeps the elements in ascending sorted order

%import math
%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print("empty list:\n")
        printlist()

        ^^slist.Node new = arena.alloc(sizeof(slist.Node))
        new.size = 500
        new.letter = 'k'
        slist.add(new)
        txt.print("\nafter inserting 1 element:\n")
        printlist()

        repeat 10 {
            new = arena.alloc(sizeof(slist.Node))
            new.size = math.rndw() % 1000
            new.letter = math.rnd() % 26 + 'a'
            slist.add(new)
        }
        txt.print("\nafter inserting 10 more random elements:\n")
        printlist()
    }

    sub printlist() {
        ubyte count = 0
        ^^slist.Node n = slist.head
        while n!=0 {
            txt.print_uw(n.size)
            txt.chrout(':')
            txt.chrout(n.letter)
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
        ubyte letter
    }

    ^^Node head = 0

    sub add(^^Node node) {
        ; insert at sorted position
        node.next = 0
        if head==0 {
            head = node
        } else {
            uword size = node.size
            ^^Node predecessor = 0
            ^^Node current = head
            while current!=0 {
                if current.size >= size {
                    node.next = current
                    if predecessor!=0
                        break
                    else {
                        head = node
                        return
                    }
                }
                predecessor = current
                current = current.next
            }
            predecessor.next = node
        }
    }
}


arena {
    ; extremely trivial arena allocator (that never frees)
    uword buffer = memory("arena", 2000, 0)
    uword next = buffer

    sub alloc(ubyte size) -> uword {
        uword result = next
        next += size
        return result
    }
}
