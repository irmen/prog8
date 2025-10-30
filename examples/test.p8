main {
    struct Node1 {
        ubyte type
        word ww
    }
    struct Node2 {
        ubyte type
        ^^ubyte text
    }

    sub start() {
        ^^Node1 @shared next
        ^^Node2 @shared this
        ^^ubyte ubptr

        ubptr = next as ^^ubyte
        ubptr = 12345
        ubptr = cx16.r0
        ubptr = next as uword       ; TODO fix type error; the cast should succeed

        this.text = next as ^^ubyte
        this.text = 12345
        this.text = cx16.r0
        this.text = next as uword       ; TODO fix type error; the cast should succeed
    }
}
