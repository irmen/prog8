%import sprites
%import textio
%option no_sysinit
%zeropage basicsafe

main $0810 {
    sub start() {
        ubyte sprite

        for sprite in 1 to 99 {
            sys.wait(1)
            sprites.init(sprite, 0, 0, sprites.SIZE_8, sprites.SIZE_8, sprites.COLORS_256, 0)
            sprites.pos(sprite, 10 + sprite*10, 10+sprite*4)
        }

        sprites.reset(1, 100)

    }
}
