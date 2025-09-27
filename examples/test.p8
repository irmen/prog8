main {

    sub start() {
        ; TODO assigning to pointer indexed is not yet supported:
        sprptr[2]^^.y = 99
        sprptr[cx16.r0L]^^.y = 99
        sprites[2]^^.y = 99
        sprites[cx16.r0L]^^.y = 99
    }

    struct Sprite {
        uword x
        ubyte y
    }


    ^^Sprite[4] @shared sprites
    ^^Sprite @shared sprptr
}

