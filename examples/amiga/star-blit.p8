; Rotating five-point star with a smaller rectangle in its center -
; double-buffered bare-metal blitter version.
; No OS draw routines: fill-compatible XOR edges and one blitter.area_fill
; make the star solid while leaving the rectangle empty. The outline is
; redrawn normally afterward.
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
    ; Static COLOR01 ramp from line 48 through line 299.
    const ubyte COLOR1_RAMP_STEPS = 16
    uword[COLOR1_RAMP_STEPS] color1_slots
    ; Copper vertical positions wrap at 256; the last waits are lines 266, 282, and 299.
    ubyte[COLOR1_RAMP_STEPS] color1_wait_lines = [
        48, 65, 81, 98, 115, 132, 148, 165,
        181, 198, 215, 232, 249, 10, 26, 43]
    uword[COLOR1_RAMP_STEPS] color1_gradient = [
        $0004, $0114, $0225, $0336, $0446, $0557, $0668, $0779,
        $0889, $099a, $0aab, $0bbc, $0ccc, $0ddd, $0eee, $0fff]

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
        if not playfield.create_backplane() {
            playfield.destroy()
            return
        }
        ^^custom.DisplayRegs regs = playfield.regs
        custom.grab_system(true)
        custom.DMACON = $83C0

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

            custom.waitvsync()
            blitter.wait()
            playfield.flip()
            playfield.patch_copper(copper.list, planeBase)
            pointer bitplane = playfield.back.Planes[0]

            rotate_shape(angle)

            ; Clear the region.
            blitter.fill_rect(bitplane+FILL_START, FILL_WIDTH/16, FILL_HEIGHT, $0000, FILL_MODULO)

            ; Draw one XOR crossing per scanline, excluding horizontal edges.
            draw_outlines(bitplane, true)

            ; Fill: star solid, rectangle stays empty.
            blitter.area_fill(bitplane+FILL_START, FILL_WIDTH/16, FILL_HEIGHT, FILL_MODULO)

            ; Restore complete edges, including horizontal spans.
            ;; draw_outlines(bitplane, false)

            angle += ANGLE_STEP
        }

        blitter.wait()
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

    sub draw_outlines(pointer bitplane, bool fill) {
        ; star chain
        draw_edge(0, 1, bitplane, fill)
        draw_edge(1, 2, bitplane, fill)
        draw_edge(2, 3, bitplane, fill)
        draw_edge(3, 4, bitplane, fill)
        draw_edge(4, 5, bitplane, fill)
        draw_edge(5, 6, bitplane, fill)
        draw_edge(6, 7, bitplane, fill)
        draw_edge(7, 8, bitplane, fill)
        draw_edge(8, 9, bitplane, fill)
        draw_edge(9, 0, bitplane, fill)
        ; rectangle
        draw_edge(10, 11, bitplane, fill)
        draw_edge(11, 12, bitplane, fill)
        draw_edge(12, 13, bitplane, fill)
        draw_edge(13, 10, bitplane, fill)
    }

    sub draw_edge(ubyte first, ubyte second, pointer bitplane, bool fill) {
        uword first_offset = (first as uword) * 2
        uword second_offset = (second as uword) * 2
        blitter.line_draw(vertices[first_offset] as uword, vertices[first_offset+1] as uword,
                               vertices[second_offset] as uword, vertices[second_offset+1] as uword,
                               bitplane, false, fill)
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
        void copper.move($0182, $0004)    ; COLOR01 = dark blue (foreground)
        for i in 0 to COLOR1_RAMP_STEPS-1 {
            if i == 13
                copper.wait($ff, 0)    ; vertical position wraps after line 255
            copper.wait(color1_wait_lines[i], 0)
            color1_slots[i] = copper.move($0182, 0)
            copper.set(color1_slots[i], color1_gradient[i])
        }
        copper.end()
        return planeBase
    }
}

copperdata {
    %option amiga_chipram
    uword[128] copper_list
}
