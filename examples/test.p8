main {
    sub start() {

label:
        uword @shared addr
        addr = label
        addr = thing
        addr = &label
        addr = &thing
    }

    sub thing() {
    }
}
