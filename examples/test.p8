%import textio

main {
    sub start() {
        ^^ubyte @shared wptr

        ; three ways to write the exact same operation (getting the byte at the address this pointer points to)
        cx16.r0L = wptr^^
        cx16.r1L = wptr[0]
        cx16.r2L = @(wptr)


        ^^thing.Node n1 = thing.Node(true, -42, 0)
        ^^thing.Node n2 = thing.Node(false, 99, 0)
        n1.next = n2
        n2.next = n1
        info(n1)

        word[] @nosplit values = [111,222,-999,-888]

        ^^byte @shared bptr
        ^^ubyte @shared ubptr

        str name = "irmen"
        bptr = &name
        ubptr = &name
        stringinfo1("hello")
        stringinfo2(name)
        stringinfo3("apple")
        arrayinfo(values)
        arrayinfo(&values[2])


        bptr = name
        ubptr = name
        txt.print_uw(&name)
        txt.spc()
        txt.print_uw(bptr)
        txt.spc()
        txt.print_uw(ubptr)
        txt.nl()
    }

    sub info(^^thing.Node node) {
        txt.print_uw(node)
        txt.nl()
        txt.print_bool(node.flag)
        txt.nl()
        txt.print_b(node.value)
        txt.nl()

        node++
        txt.print_uw(node)
        txt.nl()
        txt.print_bool(node.flag)
        txt.nl()
        txt.print_b(node.value)
        txt.nl()
    }

    sub stringinfo1(^^ubyte message) {
        txt.print("string1: ")
        txt.print_uw(message)
        txt.spc()
        txt.print(message)
        txt.spc()
        do {
            txt.chrout(message^^)
            message++
        } until message^^==0
        txt.nl()
    }

    sub stringinfo2(str message) {
        txt.print("string2: ")
        txt.print_uw(message)
        txt.spc()
        txt.print(message)
        txt.spc()
        do {
            txt.chrout(message^^)
            message++
        } until message^^==0
        txt.nl()
    }

    sub stringinfo3(uword message) {
        txt.print("string3: ")
        txt.print_uw(message)
        txt.spc()
        txt.print(message)
        txt.spc()
        do {
            txt.chrout(message^^)       ; equivalent to @(message) in this case
            message++
        } until message^^==0    ; equivalent to @(message) in this case
        txt.nl()
    }

    sub arrayinfo(^^word valueptr) {
        txt.print_uw(valueptr)
        txt.spc()
        txt.print_w(valueptr^^)
        txt.nl()
        valueptr++
        txt.print_uw(valueptr)
        txt.spc()
        txt.print_w(valueptr^^)
        txt.nl()
    }
}

thing {
    struct Node {
        bool flag
        byte value
        ^^Node next
    }
}
