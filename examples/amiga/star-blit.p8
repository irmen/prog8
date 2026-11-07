; Rotating five-point star with a smaller rectangle in its center -
; bare-metal blitter version.
; No OS draw routines: star and rectangle outlines via blitter.line_draw
; with fill=true (ONEDOT lines), interior via one blitter.area_fill; the
; fill's parity rule makes the star solid and leaves the rectangle empty.
; The pentagon radius is 120, so the fill region is 256x256 centered on
; the plane. Click or ESC exits.

%import custom
%import playfield
%import copper
%import exec
%import blitter
%import math

main {
    const word CX = 160
    const word CY = 128
    const ubyte ANGLE_STEP = 1

    ; Fill region: 256 px wide, word aligned, covering the rotating
    ; radius-120 shape (worst-case x extent: 40..280).
    const uword FILL_WIDTH = 256
    const uword FILL_HEIGHT = 256
    const uword FILL_MODULO = (320-FILL_WIDTH)/8
    const uword FILL_START = (320/2 - FILL_WIDTH/2)/8

    ; Star: 5 outer points (pentagon radius 120, inner radius 48)
    ; alternating with 5 inner corners; then the 32x32 center rectangle.
    word[28] base_vertices = [
        160, 8,    188, 89,   274, 91,   206, 143,  231, 225,
        160, 176,  89, 225,   114, 143,  46, 91,    132, 89,
        144, 112,  176, 112,  176, 144,  144, 144]
    word[28] vertices

    sub start() {
        if not playfield.create(320, 256, 320, 256, 1, custom.DISPLAY_DEFAULT, 0)
            return
        ^^custom.DisplayRegs regs = playfield.regs
        custom.grab_system(true)
        custom.DMACON = $83C0

        pointer bitplane = playfield.bitmap.Planes[0]

        blitter.line_init($ffff, $8000, $ffff, 40)

        uword planeBase = build_copperlist(regs)
        playfield.patch_copper(copper.list, planeBase)
        custom.COP1LC = copper.list
        custom.DMACON = $8280    ; SET | DMAEN | COPEN
        custom.COPJMP1 = 0

        ubyte angle = 0
        bool done = false
        while not done {
            if custom.left_button()
                done = true
            else {
                bool keyPressed
                ubyte keyCode
                keyPressed, keyCode = custom.raw_keyboard_poll()
                if keyPressed and (keyCode & $7f) == $45
                    done = true
            }
            if done
                break

            rotate_shape(angle)

            ; Clear the region.
            blitter.fill_rect(bitplane+FILL_START, FILL_WIDTH/16, FILL_HEIGHT, $0000, FILL_MODULO)

            ; Outline star and rectangle (absolute screen coordinates).
            draw_outlines(bitplane)

            ; Fill: star solid, rectangle stays empty.
            blitter.area_fill(bitplane+FILL_START, FILL_WIDTH/16, FILL_HEIGHT, FILL_MODULO)

            custom.waitvsync()
            angle += ANGLE_STEP
        }

        custom.FMODE = $0000
        custom.restore_system()
        playfield.destroy()
    }

    sub rotate_shape(ubyte angle) {
        byte sn = math.sin8(angle)
        byte cs = math.cos8(angle)
        word snw = sn as word
        word csw = cs as word
        for i in 0 to 13 {
            uword voff = (i as uword) * 2
            word dx = base_vertices[voff] - CX
            word dy = base_vertices[voff+1] - CY
            vertices[voff] = CX + (dx*csw - dy*snw) / 127
            vertices[voff+1] = CY + (dx*snw + dy*csw) / 127
        }
    }

    sub draw_outlines(pointer bitplane) {
        ; star chain
        draw_edge(0, 1, bitplane)
        draw_edge(1, 2, bitplane)
        draw_edge(2, 3, bitplane)
        draw_edge(3, 4, bitplane)
        draw_edge(4, 5, bitplane)
        draw_edge(5, 6, bitplane)
        draw_edge(6, 7, bitplane)
        draw_edge(7, 8, bitplane)
        draw_edge(8, 9, bitplane)
        draw_edge(9, 0, bitplane)
        ; rectangle
        draw_edge(10, 11, bitplane)
        draw_edge(11, 12, bitplane)
        draw_edge(12, 13, bitplane)
        draw_edge(13, 10, bitplane)
    }

    sub draw_edge(ubyte first, ubyte second, pointer bitplane) {
        uword first_offset = (first as uword) * 2
        uword second_offset = (second as uword) * 2
        blitter.line_draw(vertices[first_offset] as uword, vertices[first_offset+1] as uword,
                               vertices[second_offset] as uword, vertices[second_offset+1] as uword,
                               bitplane, false, true)
    }

    sub build_copperlist(^^custom.DisplayRegs display) -> uword {
        copper.init(&copperdata.copper_list, len(copperdata.copper_list))
        void copper.move($0096, $83c0)    ; DMACON: SET | DMAEN | BLTEN | BPLEN | COPEN
        uword planeBase = copper.attach_planes(1)
        void copper.move($01fc, display.fmode)
        void copper.move($0100, display.bplcon0)
        void copper.move($008e, display.diwstrt)
        void copper.move($0090, display.diwstop)
        void copper.move($0092, display.ddfstrt)
        void copper.move($0094, display.ddfstop)
        void copper.move($0108, display.bpl1mod)
        void copper.move($0180, 0)        ; COLOR00 = black (background)
        void copper.move($0182, $0fff)    ; COLOR01 = white (foreground)
        copper.end()
        return planeBase
    }
}

copperdata {
    %option amiga_chipram
    uword[128] copper_list
}
