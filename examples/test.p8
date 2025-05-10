main {
    struct MyNode {
        bool flag
        ^^MyNode next
    }

    sub start() {
        cx16.r0 = MyNode()

        ^^MyNode @shared ptr1 = cx16.r0

        ptr1 = 2000
        ptr1 = 20
        ptr1 = 20.2222
    }
}
