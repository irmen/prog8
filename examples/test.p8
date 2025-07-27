main {
    struct Enemy {
        ubyte xpos, ypos
        uword health
        bool elite
    }

    sub start() {
        bool @shared b1

        b1 = 4.44
        b1 = 99
        bfunc(4.44)
        bfunc(99)

        ^^Enemy @shared e1 = Enemy()
        ^^Enemy @shared e2 = Enemy(1,2,3,true)
        ^^Enemy @shared e3 = Enemy(1,2,3,4)
        ^^Enemy @shared e4 = Enemy(1,2,3,4.555)
    }

    sub bfunc(bool bb) {
        cx16.r0++
    }
}
