%import math

lineclip {
    %option merge, ignore_unused

    ; Based on Mark Moxon's Elite deep dive: https://elite.bbcelite.com/deep_dives/line-clipping.html

    private word cx1, cy1, cx2, cy2

    ; Set the clipping rectangle coordinates (all inclusive)
    sub set_cliprect(word x1, word y1, word x2, word y2) {
        cx1 = x1
        cy1 = y1
        cx2 = x2
        cy2 = y2
    }

    ; returns true if (x,y) is inside the clipping rectangle set by set_cliprect()
    sub inside(word x, word y) -> bool {
        return x >= cx1 and x <= cx2 and y >= cy1 and y <= cy2
    }

    ; Clip line segment (x1,y1)-(x2,y2) against the current clipping rectangle, set by set_cliprect().
    ; Returns (visible, clipped_x1, clipped_y1, clipped_x2, clipped_y2).
    sub clip(word x1, word y1, word x2, word y2) -> bool, word, word, word, word {
        bool a_inside = inside(x1, y1)
        bool b_inside = inside(x2, y2)

        if a_inside and b_inside
            return true, x1, y1, x2, y2

        ; Quick reject: both off-screen on same side
        ubyte a_code = 0
        ubyte b_code = 0
        if x1 < cx1   a_code |= 1
        if x1 > cx2   a_code |= 2
        if y1 < cy1   a_code |= 4
        if y1 > cy2   a_code |= 8
        if x2 < cx1   b_code |= 1
        if x2 > cx2   b_code |= 2
        if y2 < cy1   b_code |= 4
        if y2 > cy2   b_code |= 8

        if (a_code & b_code) != 0
            return false, 0, 0, 0, 0

        word ddx = x2 - x1
        word ddy = y2 - y1
        if not a_inside
            x1, y1 = clip_one(x1, y1, ddx, ddy)
        if not b_inside
            x2, y2 = clip_one(x2, y2, -ddx, -ddy)

        if not (inside(x1, y1) and inside(x2, y2))
            return false, 0, 0, 0, 0

        return true, x1, y1, x2, y2


        sub clip_one(word ax, word ay, word odx, word ody) -> word, word {

            word x, y
            bool did_clip

            repeat 2 {
                x = ax
                y = ay
                did_clip = false

                if x < cx1 and odx > 0 {
                    y += frac(cx1 - x, ody, odx)
                    x = cx1
                    did_clip = true
                } else if x > cx2 and odx < 0 {
                    y += frac(cx2 - x, ody, odx)
                    x = cx2
                    did_clip = true
                }

                if y < cy1 and ody > 0 {
                    x += frac(cy1 - y, odx, ody)
                    y = cy1
                    did_clip = true
                } else if y > cy2 and ody < 0 {
                    x += frac(cy2 - y, odx, ody)
                    y = cy2
                    did_clip = true
                }

                ax = x
                ay = y
                if not did_clip
                    break
            }
            return ax, ay

            ; (int16_t)((int32_t)dist * num / den)
            ; Uses mulu.w (16x16→32) + divu.w (32/16→16) — works on all 68000-family CPUs
            sub frac(word dist, word num, word den) -> word {
                uword adist = abs(dist) as uword
                uword anum = abs(num) as uword
                uword aden = abs(den) as uword
                uword result_u

                %asm {{
                    moveq  #0,d0
                    move.w p8b_lineclip.p8s_clip.p8s_clip_one.p8s_frac.p8v_adist,d0
                    moveq  #0,d1
                    move.w p8b_lineclip.p8s_clip.p8s_clip_one.p8s_frac.p8v_anum,d1
                    mulu.w d1,d0
                    move.w p8b_lineclip.p8s_clip.p8s_clip_one.p8s_frac.p8v_aden,d1
                    divu.w d1,d0
                    move.w d0,p8b_lineclip.p8s_clip.p8s_clip_one.p8s_frac.p8v_result_u
                }}

                if (dist < 0) xor (num < 0) xor (den < 0)
                    return -(result_u as word)
                return result_u as word
            }
        }
    }
}
