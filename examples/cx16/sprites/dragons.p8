%import diskio
%import textio
%import sprites
%zeropage basicsafe
%option no_sysinit

; an example that displays and then moves many sprites at once.

main {
    ; we choose arbitrary unused vram location for sprite data: $12000
    const ubyte SPRITE_DATA_BANK = 1
    const uword SPRITE_DATA_ADDR = $2000
    const ubyte SPRITE_PALETTE_OFFSET_IDX = 16

    const ubyte NUM_DRAGONS = 25
    word[NUM_DRAGONS*2] xpositions
    word[NUM_DRAGONS*2] ypositions

    sub start() {
        txt.plot(30,30)
        txt.print("there be many dragons!")

        ; load the sprite data and color palette directly into Vera ram
        void diskio.vload_raw("dragonsprite.bin", SPRITE_DATA_BANK, SPRITE_DATA_ADDR)
        void diskio.vload_raw("dragonsprite.pal", 1, $fa00 + SPRITE_PALETTE_OFFSET_IDX*2)

        ; initialize the dragon sprites (every dragon needs 2 sprites, top and bottom half)
        ubyte sprite_num
        for sprite_num in 0 to NUM_DRAGONS*2-2 step 2 {
            sprites.init(sprite_num+1, SPRITE_DATA_BANK, SPRITE_DATA_ADDR, sprites.SIZE_64, sprites.SIZE_64, sprites.COLORS_16, SPRITE_PALETTE_OFFSET_IDX)
            sprites.init(sprite_num+2, SPRITE_DATA_BANK, SPRITE_DATA_ADDR + 64*64/2, sprites.SIZE_64, sprites.SIZE_64, sprites.COLORS_16, SPRITE_PALETTE_OFFSET_IDX)

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
                if sprite_num & 2 {
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
