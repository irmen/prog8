%option enable_floats
%zeropage dontuse

main {
    sub start() {
        struct List {
            uword s
            bool b
            float f
            ^^ubyte p
        }
        struct Node {
            uword value
            ^^Node next
        }
        ^^List l1 = List()
        ^^List l2 = List(1234,true,9.876,50000)
        ^^Node n1 = Node()
        ^^Node n2 = Node(1234, 50000)
        cx16.r0 = l1
        cx16.r1 = l2
        cx16.r0 = n1
        cx16.r1 = n2
        ;l.s[2] = 42
    }
}
