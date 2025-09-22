main {

    sub start() {
        ubyte @shared ok = sprites[2].y         ; this one is fine...
        ubyte @shared y = sprites[2].y         ; TODO fix crash
    }

    struct Sprite {
        uword x
        ubyte y
    }


    ^^Sprite[4] @shared sprites
}

