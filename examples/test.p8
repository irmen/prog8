main {

    sub start() {
        struct Node {
            ^^uword s
        }

        ^^Node l1

        l1.s[0] = 4242
        l1^^.s[0] = 4242        ; TODO fix parse error
    }
}
