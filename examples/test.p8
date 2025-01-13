%import floats
%import sprites
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {

        cx16.mouse_config2(1)

        sprites.init(1, 0, 0, sprites.SIZE_64, sprites.SIZE_64, sprites.COLORS_16, 0)

        sprites.pos(1, 100, 100)

        repeat {
            word x,y
            x,y = sprites.getxy(0)
            sprites.pos(1, x, y)
        }

        float fz
        cx16.r0L = single()
        cx16.r0L, fz = multi()
    }

    sub single() -> ubyte {
        return xx
    }

    sub multi() -> ubyte, float {
        return xx, 3.33
    }
}
