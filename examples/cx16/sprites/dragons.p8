%import math
%import diskio
%import textio
%import sprites
%zeropage basicsafe
%option no_sysinit

; an example that displays and then moves many sprites at once.

main {
    const long SPRITE_DATA = $12000
    const ubyte SPRITE_PALETTE_OFFSET = 1      ; sprite palette at color index 16

    const ubyte NUM_DRAGONS = 25
    word[NUM_DRAGONS*2] xpositions
    word[NUM_DRAGONS*2] ypositions

    sub start() {
        txt.plot(30,30)
        txt.print("there be many dragons!")

        ; load the sprite data and color palette directly into Vera ram
        void diskio.vload_raw("dragonsprite.bin", bankof(SPRITE_DATA), SPRITE_DATA & $ffff)
        void diskio.vload_raw("dragonsprite.pal", 1, $fa00 + SPRITE_PALETTE_OFFSET*16*2)

        ; initialize the dragon sprites (every dragon needs 2 sprites, top and bottom half)
        ubyte sprite_num
        for sprite_num in 0 to NUM_DRAGONS*2-2 step 2 {
            sprites.init(sprite_num+1, bankof(SPRITE_DATA), SPRITE_DATA & $ffff, sprites.SIZE_64, sprites.SIZE_64, sprites.COLORS_16, SPRITE_PALETTE_OFFSET)
            sprites.init(sprite_num+2, bankof(SPRITE_DATA), (SPRITE_DATA & $ffff) + 64*64/2, sprites.SIZE_64, sprites.SIZE_64, sprites.COLORS_16, SPRITE_PALETTE_OFFSET)

            xpositions[sprite_num] = math.rndw() % (640-64) as word
            xpositions[sprite_num+1] = xpositions[sprite_num]
            ypositions[sprite_num] = sprite_num * $0008 as word
            ypositions[sprite_num+1] = ypositions[sprite_num]+64
        }

        repeat {
            ; move all dragons (remember each one consists of a top and a bottom sprite)
            for sprite_num in 0 to NUM_DRAGONS*2-2 step 2 {
                xpositions[sprite_num]++
                xpositions[sprite_num+1]++
                if sprite_num & 2 !=0 {
                    xpositions[sprite_num]++
                    xpositions[sprite_num+1]++
                }
                if xpositions[sprite_num] >= 640
                    xpositions[sprite_num] = -64
                if xpositions[sprite_num+1] >= 640
                    xpositions[sprite_num+1] = -64
            }

            sys.waitvsync()
            sprites.pos_batch(1, NUM_DRAGONS*2, &xpositions, &ypositions)
        }
    }
}
