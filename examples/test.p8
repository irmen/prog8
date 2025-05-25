main {
    sub start() {
        struct List {
            ^^uword s
            uword n
        }
        ^^List  l = List()

        cx16.r0 = l.s[1]
        cx16.r1 = l^^.s[2]
        l.s[1] = 4242
        l.s[2] = 4242
        l.s[1] = 42
        l.s[2] = 42
    }
}
