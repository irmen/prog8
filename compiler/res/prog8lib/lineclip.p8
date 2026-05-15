%import math

lineclip {

    ; Based on Mark Moxon's Elite deep dive: https://elite.bbcelite.com/deep_dives/line-clipping.html

    word cx1, cy1, cx2, cy2

    ; Set the clipping rectangle coordinates
    sub set_cliprect(word x1, word y1, word x2, word y2) {
        cx1 = x1
        cy1 = y1
        cx2 = x2
        cy2 = y2
    }

    ; Clip line segment (x1,y1)-(x2,y2) against the current clipping rectangle, set with set_cliprect().
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
            sub frac(word dist, word num, word den) -> word {
                ; 16×16→32 multiply, R starts as upper word of product
                alias Q_lo = cx16.r0
                alias Q_hi = cx16.r1
                alias R = cx16.r2
                alias aden = cx16.r3

                ; No long division available on 6502, so we use restoring division 32/16->16
                aden = abs(den) as uword
                Q_hi = abs(dist) * abs(num)
                R = math.mul16_last_upper()
                Q_lo = 0
                repeat 16 {
                    R = R << 1
                    if (Q_hi & $8000) != 0
                        R |= 1
                    Q_hi <<= 1
                    if (Q_lo & $8000) != 0
                        Q_hi |= 1
                    Q_lo <<= 1
                    if R >= aden {
                        R -= aden
                        Q_lo |= 1
                    }
                }

                if (dist < 0) xor (num < 0) xor (den < 0)
                    return -(Q_lo as word)
                return Q_lo as word

                /*
                here is the implementation IF an efficient long division was available:

                uword adist = abs(dist) as uword
                uword anum = abs(num) as uword
                uword aden = abs(den) as uword

                ; do 16*16->32 bit multiplication instead of slower full 32*32
                cx16.r2 = adist * anum
                long product32 = mklong2(math.mul16_last_upper(), cx16.r2)

                ; unfortunately the long division is still needed in prog8
                uword result_u = (product32 / (aden as long)) as uword
                word result = result_u as word
                if (dist < 0) xor (num < 0) xor (den < 0)
                    result = -result
                return result

                */
            }
        }

        sub inside(word x, word y) -> bool {
            return x >= cx1 and x <= cx2 and y >= cy1 and y <= cy2
        }
    }
}
