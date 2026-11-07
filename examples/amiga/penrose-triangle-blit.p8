; Penrose impossible triangle - bare-metal blitter version.
; No OS draw routines: outlines via blitter.line_draw with fill=true (ONEDOT
; lines), interior via blitter.area_fill.
; Rotates ~20 degrees per frame around the figure center; ESC or left click exits.

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

    const ubyte V_TOP_LEFT = 0
    const ubyte V_TOP_RIGHT = 1
    const ubyte V_RIGHT = 2
    const ubyte V_BOTTOM_RIGHT = 3
    const ubyte V_BOTTOM_LEFT = 4
    const ubyte V_LEFT = 5
    const ubyte V_MID_EAST = 6
    const ubyte V_MID_WEST = 7
    const ubyte V_INNER_SOUTH = 8
    const ubyte V_INNER_NORTH = 9
    const ubyte V_FOLD_NORTH = 10
    const ubyte V_FOLD_SOUTH = 11

    word[24] base_vertices = [151, 29, 169, 29, 250, 169,
                              241, 185, 79, 185, 70, 169,
                              205, 154, 97, 154, 115, 154,
                              169, 60, 160, 76, 214, 169]
    word[24] vertices

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
            draw_wireframe(bitplane)
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
        for i in 0 to 11 {
            uword voff = (i as uword) * 2
            word dx = base_vertices[voff] - CX
            word dy = base_vertices[voff+1] - CY
            vertices[voff] = CX + (dx*csw - dy*snw) / 127
            vertices[voff+1] = CY + (dx*snw + dy*csw) / 127
        }
    }

    sub draw_wireframe(pointer bitplane) {
        blitter.fill_rect(bitplane, 20, 256, 0, 0)
        blitter.wait()

        draw_edge(V_TOP_LEFT, V_TOP_RIGHT, bitplane)
        draw_edge(V_TOP_RIGHT, V_RIGHT, bitplane)
        draw_edge(V_RIGHT, V_BOTTOM_RIGHT, bitplane)
        draw_edge(V_BOTTOM_RIGHT, V_BOTTOM_LEFT, bitplane)
        draw_edge(V_BOTTOM_LEFT, V_LEFT, bitplane)
        draw_edge(V_LEFT, V_TOP_LEFT, bitplane)
        draw_edge(V_MID_WEST, V_MID_EAST, bitplane)
        draw_edge(V_FOLD_SOUTH, V_LEFT, bitplane)
        draw_edge(V_MID_WEST, V_TOP_RIGHT, bitplane)
        draw_edge(V_INNER_SOUTH, V_INNER_NORTH, bitplane)
        draw_edge(V_INNER_NORTH, V_BOTTOM_RIGHT, bitplane)
        draw_edge(V_FOLD_NORTH, V_FOLD_SOUTH, bitplane)
        blitter.wait()

        ; Fill the interior between the outline edges.
        blitter.fill_rect(bitplane, 20, 256, $1010, 0)
        blitter.wait()
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
