main {
    sub start() {
        struct List {
            uword s
            uword n
        }
        ^^List  l = List()
        l.s[2] = 42
    }
}
