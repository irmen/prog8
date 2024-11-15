%import math
%import diskio
%import textio
%import sprites
%zeropage basicsafe
%option no_sysinit

; an example that displays and moves a single dragon (actually 2 sprites).

main {
    const long SPRITE_DATA = $12000
    const ubyte SPRITE_PALETTE_OFFSET = 1       ; sprite palette at color index 16

    sub start() {
        txt.plot(32,30)
        txt.print("there be dragons!")

        ; load the sprite data and color palette directly into Vera ram
        void diskio.vload_raw("dragonsprite.bin", bnk(SPRITE_DATA), SPRITE_DATA & $ffff)
        void diskio.vload_raw("dragonsprite.pal", 1, $fa00 + SPRITE_PALETTE_OFFSET*16*2)

        ; initialize the dragon sprites
        sprites.init(1, bnk(SPRITE_DATA), SPRITE_DATA & $ffff, sprites.SIZE_64, sprites.SIZE_64, sprites.COLORS_16, SPRITE_PALETTE_OFFSET)
        sprites.init(2, bnk(SPRITE_DATA), (SPRITE_DATA & $ffff) + 64*64/2, sprites.SIZE_64, sprites.SIZE_64, sprites.COLORS_16,SPRITE_PALETTE_OFFSET)

        ubyte tt = 0
        word xpos = -64
        word ypos
        bool flippedx = false

        sprites.pos(1, 0, 100)
        sprites.pos(2, 100, 300)

        repeat {
            if flippedx
                xpos -= 2
            else
                xpos += 2

            if xpos >= 640 or xpos <= -64
                flippedx = not flippedx

            ypos = (240-64 as word) + math.sin8(tt)
            tt++

            txt.plot(32, 32)
            txt.print("at: ")
            txt.print_w(sprites.getx(1))
            txt.chrout(',')
            txt.print_w(sprites.gety(1))
            txt.print("   ")

            sys.waitvsync()
            ; we set absolute sprite position, rather than the relative move() routines
            sprites.pos(1, xpos, ypos)
            sprites.pos(2, xpos, ypos+64)
            sprites.flipx(1, flippedx)
            sprites.flipx(2, flippedx)
        }
    }
}
