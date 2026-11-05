%import textio

main {
    sub start() {
        test("\nblah\n")
    }

    sub test(str x) {
        for i in x {
            txt.chrout(i)
        }
    }
}
