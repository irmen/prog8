main {
    struct Enemy {
        ubyte xpos, ypos
        uword health
        bool elite
    }

    sub start() {
        ^^Enemy @shared e1 = Enemy()
        ^^Enemy @shared e2 = Enemy(1,2,3,true)
        ^^Enemy @shared e3 = Enemy(1,2,3,4)         ; TODO type error for the boolean
        ^^Enemy @shared e4 = Enemy(1,2,3,4.555)     ; TODO type error for the boolean
    }
}
