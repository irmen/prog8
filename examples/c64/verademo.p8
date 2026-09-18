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
        txt.print("\n*** vera64 sprite demo ***\n")
        txt.print("vera base address: ")
        txt.print_uwhex(vera.VERA_BASE,true)
        txt.print("\nresetting vera and setting lores screen\n")

        vera.reset()
        init_bitmap_screen()            ; 320x240 8bpp via direct vera register pokes

        txt.print("setting some custom colors\n")
        txt.print("and animating a big sprite...\n")

        vera_palette.set_color(10, $0f8d)     ; palette index 10 = some pink color
        vera_palette.set_color(11, $0163)     ; palette index 11 = some dark green color

        ; fill 64x64 4bpp sprite data (2048 bytes) with pixel value 5
        vera.vaddr_autoincr(1, SPRITE_ADDR, 0, 1)
        repeat 2048 {
            vera.VERA_DATA0 = $AB
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

    sub init_bitmap_screen() {
        ; 320x240 8bpp bitmap on layer 1, bitmap at vram $00000, set via direct vera register pokes
        vera.VERA_CTRL = 0
        vera.VERA_DC_VIDEO = (vera.VERA_DC_VIDEO & %11001100) | %00100001     ; layer 1 only, force VGA output (a vera reset leaves output mode 0=disabled)
        vera.VERA_DC_HSCALE = 64
        vera.VERA_DC_VSCALE = 64
        vera.VERA_L1_CONFIG = %00000111     ; bitmap mode, 8 bpp
        vera.VERA_L1_MAPBASE = 0
        vera.VERA_L1_TILEBASE = 0           ; bitmap base $00000
        vera.VERA_L1_HSCROLL_L = 0
        vera.VERA_L1_HSCROLL_H = 0
        vera.VERA_L1_VSCROLL_L = 0
        vera.VERA_L1_VSCROLL_H = 0
    }
}
