main {
    sub start() {
        recurse1()
    }
    sub recurse1() {
        recurse2()
    }
    sub recurse2() {
        uword @shared address = &start
    }
}
