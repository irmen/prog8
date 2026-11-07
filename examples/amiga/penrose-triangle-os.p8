; Penrose impossible triangle on a PAL low-resolution screen.
; Spinning version: outlines via easygfx.line, faces via boundary flood fills.
; Rotates ~20 degrees per frame around the figure center; ESC or left click exits.

%import easygfx
%import graphics
%import intuition
%import exec
%import math

main {
    const ubyte PEN_WHITE = 0
    const ubyte PEN_EDGE = 1
    const ubyte PEN_GREY = 2
    const ubyte PEN_BLACK = 3

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

    ; Rotation center: the figure's 3-fold symmetry center (the equilateral
    ; centroid), at screen center.
    const word CX = 160
    const word CY = 128
    ; 256 angle steps per revolution, so 14 steps is just under 20 degrees.
    const ubyte ANGLE_STEP = 14

    ; Unrotated master geometry (canonical equilateral construction); all
    ; slanted edges use the square-pixel 60-degree slope (dy/dx = sqrt 3),
    ; so the whole figure is 3-fold rotationally symmetric around (CX,CY).
    ; rotate_shape() derives vertices/fills from these.
    word[24] base_vertices = [151, 29, 169, 29, 250, 169,
                              241, 185, 79, 185, 70, 169,
                              205, 154, 97, 154, 115, 154,
                              169, 60, 160, 76, 214, 169]
    word[24] vertices
    word[6] base_fills = [169, 41, 160, 165, 160, 173]
    word[6] fills

    uword[4] palette = [$0fff, $0bbb, $0888, $0000]

    sub start() {
        ; 320x256 lores is the PAL display size; 2 bitplanes provide 4 pens.
        if easygfx.fullscreen(320, 256, 2, graphics.LORES, "") {
            defer easygfx.close()
            easygfx.palette(palette, len(palette))
            ubyte angle = 0
            bool done = false
            while not done {
                ; Non-blocking message pump so the animation keeps running.
                ^^intuition.IntuiMessage msg = exec.GetMsg(easygfx.window.UserPort) as ^^intuition.IntuiMessage
                while msg != 0 {
                    when msg.Class {
                        intuition.IDCMP_RAWKEY -> {
                            if (msg.Code & $80) == 0 and (msg.Code & $7f) == $45
                                done = true
                        }
                        intuition.IDCMP_MOUSEBUTTONS -> {
                            if msg.Code == intuition.SELECTDOWN
                                done = true
                        }
                    }
                    exec.ReplyMsg(msg as ^^exec.Message)
                    msg = exec.GetMsg(easygfx.window.UserPort) as ^^intuition.IntuiMessage
                }
                if done
                    break
                rotate_shape(angle)
                draw_triangle()
                angle += ANGLE_STEP

                sys.wait(180)
            }
        }
    }

    sub rotate_shape(ubyte angle) {
        ; Rotate base vertices and fill seeds around (CX, CY).
        ; math.sin8/cos8 return -127..127, so the products are divided by 127.
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
        for i in 0 to 2 {
            uword foff = (i as uword) * 2
            word fx = base_fills[foff] - CX
            word fy = base_fills[foff+1] - CY
            fills[foff] = CX + (fx*csw - fy*snw) / 127
            fills[foff+1] = CY + (fx*snw + fy*csw) / 127
        }
    }

    sub draw_triangle() {
        easygfx.clear(PEN_WHITE)

        ; Outer hexagon with clipped corners.
        draw_edge(V_TOP_LEFT, V_TOP_RIGHT)
        draw_edge(V_TOP_RIGHT, V_RIGHT)
        draw_edge(V_RIGHT, V_BOTTOM_RIGHT)
        draw_edge(V_BOTTOM_RIGHT, V_BOTTOM_LEFT)
        draw_edge(V_BOTTOM_LEFT, V_LEFT)
        draw_edge(V_LEFT, V_TOP_LEFT)

        ; Three overlap bars plus the inner opening.
        draw_edge(V_MID_WEST, V_MID_EAST)
        draw_edge(V_FOLD_SOUTH, V_LEFT)
        draw_edge(V_MID_WEST, V_TOP_RIGHT)
        draw_edge(V_INNER_SOUTH, V_INNER_NORTH)
        draw_edge(V_INNER_NORTH, V_BOTTOM_RIGHT)
        draw_edge(V_FOLD_NORTH, V_FOLD_SOUTH)

        ; Boundary fills use the black outline pen as the stopping border.
        easygfx.fillto(fills[2], fills[3], PEN_GREY, PEN_EDGE)
        easygfx.fillto(fills[4], fills[5], PEN_BLACK, PEN_EDGE)
        easygfx.fillto(fills[0], fills[1], PEN_EDGE, PEN_EDGE)
    }

    sub draw_edge(ubyte first, ubyte second) {
        uword first_offset = (first as uword) * 2
        uword second_offset = (second as uword) * 2
        easygfx.line(vertices[first_offset], vertices[first_offset+1], vertices[second_offset], vertices[second_offset+1], PEN_EDGE)
    }
}
