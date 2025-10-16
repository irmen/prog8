main {
    struct element {
        ubyte type
        long  x
        long  y
    }

    sub start() {
        ^^element myElement
        myElement.y += 1
    }
}
