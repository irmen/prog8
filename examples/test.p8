main {

    sub start() {
        sprptr[2]^^.y++
    }

    struct Sprite {
        ubyte x
        uword y
    }

    ^^Sprite[4] @shared sprites
    ^^Sprite @shared sprptr
}

