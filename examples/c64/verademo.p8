; a preliminary demo for the VERA64 support libraries

%import vera
%import math
%import textio
%zeropage basicsafe
%option no_sysinit

main {
    const uword SPRITE_ADDR = $3000     ; vera bank 1 -> vram $13000, after the 320x240 8bpp bitmap ($00000-$12bff), 32-byte aligned
    const ubyte SPRITE_NUM = 2

    sub start() {
        txt.print("\n*** vera64 demo ***\n")
        txt.print("vera base address: ")
        txt.print_uwhex(vera.VERA_BASE,true)

        vera.reset()
        vera_gfx.init()
        drawgfx()

        vera_palette.set_color(10, $0f8d)     ; palette index 10 = some pink color
        vera_palette.set_color(11, $0163)     ; palette index 11 = some dark green color

        ; fill 64x64 4bpp sprite data (2048 bytes) with pixel value 5
        vera.vaddr_autoincr(1, SPRITE_ADDR, 0, 1)
        repeat 2048/2 {
            vera.VERA_DATA0 = $AB
            vera.VERA_DATA0 = $BA
        }

        vera_sprites.init(SPRITE_NUM, 1, SPRITE_ADDR, vera_sprites.SIZE_64, vera_sprites.SIZE_64, vera_sprites.COLORS_16, 0)
        vera_sprites.show(SPRITE_NUM)

        ubyte angle = 0
        repeat {
            word xpos = 128 + (math.sin8(angle) as word) * 60 / 127
            word ypos = 88 + (math.cos8(angle) as word) * 50 / 127
            vera_sprites.pos(SPRITE_NUM, xpos, ypos)
            sys.waitvsync()
            angle++
        }
    }

    sub drawgfx() {
        for radius in 100 downto 10 step -10 {
            vera_gfx.disc(160, 120, radius, radius)
        }

        for x in 0 to 319 step 10 {
            verafx.line(x, 0, 319-x, 239, lsb(x))
        }

        for y in 0 to 239 step 10 {
            verafx.line(0, y, 319, 239-y, y)
        }

        for x in 20 to 80 step 10 {
            vera_gfx.text(x, lsb(x), lsb(x), sc:"hello from vera64 and prog8")
        }
    }

}
