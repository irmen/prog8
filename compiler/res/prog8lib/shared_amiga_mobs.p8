; mobs - shared movable-object descriptors and pure-integer geometry helpers.
; Shared by amiga500 and amiga1200 targets.

%import math

mobs {
    %option merge, no_symbol_prefixing, ignore_unused

    ; mobs - shared movable-object descriptor types for the Amiga bare-metal
    ; helpers. Pure data, no OS or library dependencies: the metadata (Mob) is
    ; shared by the renderers, and each renderer-specific struct (Bob, Sprite)
    ; embeds a Mob plus its own image pointers. Keeping all three together means
    ; a program that uses both BOBs and sprites imports one types module.
    ;
    ; Client code owns animation timing, frame selection and positions; these
    ; structs never mutate themselves and carry no hidden state. Image pointers
    ; are always in chip RAM unless noted.

    struct Mob {
        ; Movable-object metadata shared by every renderer. No bitmap pointers,
        ; no OS dependencies.
        ; x_pos/y_pos is the screen position of the anchor (origin point);
        ; origin_x/origin_y is the offset from that anchor to the image's
        ; top-left, so the top-left is (x_pos + origin_x, y_pos + origin_y).
        ; With origin (0, 0) the anchor is the top-left.
        uword id
        word x_pos
        word y_pos
        word origin_x
        word origin_y
        uword width
        uword height
        uword radius ; circle collision radius centred on the anchor; 0 = not set, box routines are used instead
        uword state
    }

    struct Bob {
        ; Blitter object. References a Mob for position/size and carries the
        ; plane-major image data and its separate mask. Client code updates
        ; data and mask before drawing when switching frames.
        ^^mobs.Mob mob
        uword depth
        pointer data
        pointer mask
    }

    struct Sprite {
        ; Hardware sprite descriptor. References a Mob for position/size.
        ; Sprite-specific payload fields are still being defined; the data
        ; pointer is the only one that is settled so far.
        ^^mobs.Mob mob
        pointer data
        ; TODO: aga width bits (32/64 px), palette bank (BPLCON4), FMODE
        ;       fetch width, and any cached SPRxCTL control word. None of
        ;       these are needed for the 16-pixel 4-colour OCS path.
    }

    asmsub collides(^^mobs.Mob a @A0, ^^mobs.Mob b @A1) -> bool @Pz {
        ; Axis-aligned bounding-box overlap, origin-aware. The box of a is
        ; [a.x_pos + a.origin_x, a.x_pos + a.origin_x + a.width) x
        ; [a.y_pos + a.origin_y, a.y_pos + a.origin_y + a.height), and
        ; likewise for b. When both origins are zero the adds are skipped.
        ; Z set = the two boxes overlap, Z clear = they do not.
        ; Broad phase only: non-rectangular shapes pass this test when their
        ; boxes overlap. Use CLXCON/CLXDAT for pixel-perfect sprite
        ; collision, or a bitmask AND of the masks for BOBs.
        %asm {{
            ; fast path: both origins zero on both mobs
            tst.w   mobs.Mob.p8v_origin_x(a0)
            bne.s   .full
            tst.w   mobs.Mob.p8v_origin_y(a0)
            bne.s   .full
            tst.w   mobs.Mob.p8v_origin_x(a1)
            bne.s   .full
            tst.w   mobs.Mob.p8v_origin_y(a1)
            bne.s   .full
            ; fast path: boxes are [x_pos, x_pos+width) x [y_pos, y_pos+height); d0/d1 only
            move.w  mobs.Mob.p8v_x_pos(a0),d0       ; a.x (= a.left)
            add.w   mobs.Mob.p8v_width(a0),d0       ; a.right
            move.w  mobs.Mob.p8v_x_pos(a1),d1       ; b.x (= b.left)
            cmp.w   d1,d0                           ; a.right - b.left
            ble.s   .no
            add.w   mobs.Mob.p8v_width(a1),d1       ; b.right
            cmp.w   mobs.Mob.p8v_x_pos(a0),d1       ; b.right - a.left
            ble.s   .no
            move.w  mobs.Mob.p8v_y_pos(a0),d0       ; a.y (= a.top)
            add.w   mobs.Mob.p8v_height(a0),d0      ; a.bottom
            move.w  mobs.Mob.p8v_y_pos(a1),d1       ; b.y (= b.top)
            cmp.w   d1,d0                           ; a.bottom - b.top
            ble.s   .no
            add.w   mobs.Mob.p8v_height(a1),d1      ; b.bottom
            cmp.w   mobs.Mob.p8v_y_pos(a0),d1       ; b.bottom - a.top
            ble.s   .no
            moveq   #0,d0                           ; Z set = collision
            rts
.no:
            moveq   #1,d0                           ; Z clear = no collision
            rts
.full:
            ; origin-aware path: box = [x_pos+origin, x_pos+origin+size)
            move.w  mobs.Mob.p8v_x_pos(a0),d0       ; a.x
            add.w   mobs.Mob.p8v_origin_x(a0),d0    ; a.left
            add.w   mobs.Mob.p8v_width(a0),d0       ; a.right
            move.w  mobs.Mob.p8v_x_pos(a1),d1       ; b.x
            add.w   mobs.Mob.p8v_origin_x(a1),d1    ; b.left
            cmp.w   d1,d0                           ; a.right - b.left
            ble.s   .no
            add.w   mobs.Mob.p8v_width(a1),d1       ; b.right
            move.w  mobs.Mob.p8v_x_pos(a0),d0       ; a.x
            add.w   mobs.Mob.p8v_origin_x(a0),d0    ; a.left
            cmp.w   d0,d1                           ; b.right - a.left
            ble.s   .no
            move.w  mobs.Mob.p8v_y_pos(a0),d0       ; a.y
            add.w   mobs.Mob.p8v_origin_y(a0),d0    ; a.top
            add.w   mobs.Mob.p8v_height(a0),d0      ; a.bottom
            move.w  mobs.Mob.p8v_y_pos(a1),d1       ; b.y
            add.w   mobs.Mob.p8v_origin_y(a1),d1    ; b.top
            cmp.w   d1,d0                           ; a.bottom - b.top
            ble.s   .no
            add.w   mobs.Mob.p8v_height(a1),d1      ; b.bottom
            move.w  mobs.Mob.p8v_y_pos(a0),d0       ; a.y
            add.w   mobs.Mob.p8v_origin_y(a0),d0    ; a.top
            cmp.w   d0,d1                           ; b.bottom - a.top
            ble.s   .no
            moveq   #0,d0                           ; Z set = collision
            rts
        }}
    }

    asmsub contains_point(^^mobs.Mob m @A0, word px @D0, word py @D1) -> bool @Pz {
        ; Point-in-box test, origin-aware. The box of m is
        ; [m.x_pos + m.origin_x, m.x_pos + m.origin_x + m.width) x
        ; [m.y_pos + m.origin_y, m.y_pos + m.origin_y + m.height).
        ; When origin is (0,0) the adds are skipped.
        ; Z set = the point is inside, Z clear = outside.
        %asm {{
            ; fast path: origin (0,0)
            tst.w   mobs.Mob.p8v_origin_x(a0)
            bne.s   .full
            tst.w   mobs.Mob.p8v_origin_y(a0)
            bne.s   .full
            ; fast path: box = [x_pos, x_pos+width) x [y_pos, y_pos+height); d0/d1 only
            sub.w   mobs.Mob.p8v_x_pos(a0),d0       ; px - x_pos
            blt.s   .no
            cmp.w   mobs.Mob.p8v_width(a0),d0       ; (px - x_pos) - width
            bge.s   .no
            sub.w   mobs.Mob.p8v_y_pos(a0),d1       ; py - y_pos
            blt.s   .no
            cmp.w   mobs.Mob.p8v_height(a0),d1      ; (py - y_pos) - height
            bge.s   .no
            moveq   #0,d0                           ; Z set = inside
            rts
.no:
            moveq   #1,d0                           ; Z clear = outside
            rts
.full:
            sub.w   mobs.Mob.p8v_x_pos(a0),d0       ; px - x_pos
            sub.w   mobs.Mob.p8v_origin_x(a0),d0    ; px - left
            blt.s   .no
            cmp.w   mobs.Mob.p8v_width(a0),d0       ; (px - left) - width
            bge.s   .no
            sub.w   mobs.Mob.p8v_y_pos(a0),d1       ; py - y_pos
            sub.w   mobs.Mob.p8v_origin_y(a0),d1    ; py - top
            blt.s   .no
            cmp.w   mobs.Mob.p8v_height(a0),d1      ; (py - top) - height
            bge.s   .no
            moveq   #0,d0                           ; Z set = inside
            rts
        }}
    }

    sub distance_sq(^^mobs.Mob a, ^^mobs.Mob b) -> long {
        ; Squared distance between the two anchors (x_pos, y_pos). No sqrt, so this is the cheap form for comparisons.
        ; dx/dy can reach ±$ffff from word coords, whose square overflows signed long; fine for real screen sizes.
        long dx = a.x_pos - b.x_pos
        long dy = a.y_pos - b.y_pos
        return dx * dx + dy * dy
    }

    inline sub distance(^^mobs.Mob a, ^^mobs.Mob b) -> uword {
        ; Euclidean distance between the two anchors, rounded down.
        return sqrt(distance_sq(a, b))
    }

    inline sub left(^^mobs.Mob m) -> word {
        ; Real left edge of the mob's box: x_pos + origin_x.
        return m.x_pos + m.origin_x
    }

    inline sub top(^^mobs.Mob m) -> word {
        ; Real top edge of the mob's box: y_pos + origin_y.
        return m.y_pos + m.origin_y
    }

    inline sub right(^^mobs.Mob m) -> word {
        ; Right edge of the box (exclusive): left + width.
        return m.x_pos + m.origin_x + (m.width as word)
    }

    inline sub bottom(^^mobs.Mob m) -> word {
        ; Bottom edge of the box (exclusive): top + height.
        return m.y_pos + m.origin_y + (m.height as word)
    }

    inline sub center_x(^^mobs.Mob m) -> word {
        ; Horizontal centre of the box.
        return m.x_pos + m.origin_x + ((m.width / 2) as word)
    }

    inline sub center_y(^^mobs.Mob m) -> word {
        ; Vertical centre of the box.
        return m.y_pos + m.origin_y + ((m.height / 2) as word)
    }

    sub delta(^^mobs.Mob a, ^^mobs.Mob b) -> word, word {
        ; Signed displacement from a's anchor to b's anchor: dx, dy (b minus a).
        ; Use delta_x/delta_y when you need the values inside an expression.
        return b.x_pos - a.x_pos, b.y_pos - a.y_pos
    }

    inline sub delta_x(^^mobs.Mob a, ^^mobs.Mob b) -> word {
        ; Horizontal signed displacement from a's anchor to b's anchor.
        return b.x_pos - a.x_pos
    }

    inline sub delta_y(^^mobs.Mob a, ^^mobs.Mob b) -> word {
        ; Vertical signed displacement from a's anchor to b's anchor (screen y, down is positive).
        return b.y_pos - a.y_pos
    }

    inline sub manhattan_distance(^^mobs.Mob a, ^^mobs.Mob b) -> uword {
        ; |dx| + |dy| between the anchors: grid walking distance, no multiply or sqrt.
        return abs(a.x_pos - b.x_pos) + abs(a.y_pos - b.y_pos)
    }

    inline sub chebyshev_distance(^^mobs.Mob a, ^^mobs.Mob b) -> uword {
        ; max(|dx|, |dy|) between the anchors: king-move distance, no multiply or sqrt.
        return max(abs(a.x_pos - b.x_pos), abs(a.y_pos - b.y_pos))
    }

    sub collides_circle(^^mobs.Mob a, ^^mobs.Mob b) -> bool {
        ; Circle collision using each mob's radius field: do the two circles
        ; centred on the anchors overlap? Compares squared distances with
        ; 16x16 multiplies, so no sqrt and no 32-bit multiply helper.
        ; Touching exactly (distance == ra + rb) counts as colliding.
        ; A radius of 0 means "not set": when either mob has none, this
        ; falls back to the rectangular collides() test.
        if a.radius == 0 or b.radius == 0 {
            return collides(a, b)
        }
        uword adx = abs(a.x_pos - b.x_pos) as uword
        uword ady = abs(a.y_pos - b.y_pos) as uword
        uword rsum = a.radius + b.radius
        return math.mul32(adx, adx) + math.mul32(ady, ady) <= math.mul32(rsum, rsum)
    }

    sub contains_circle(^^mobs.Mob m, word px, word py) -> bool {
        ; Is the point inside the circle centred on the mob anchor with its
        ; radius field? Squared comparison, no sqrt; exact boundary counts.
        ; A radius of 0 falls back to the rectangular contains_point() test.
        if m.radius == 0 {
            return contains_point(m, px, py)
        }
        uword adx = abs(px - m.x_pos) as uword
        uword ady = abs(py - m.y_pos) as uword
        return math.mul32(adx, adx) + math.mul32(ady, ady) <= math.mul32(m.radius, m.radius)
    }

    asmsub direction_to(^^mobs.Mob a @A0, ^^mobs.Mob b @A1) -> ubyte @D0 {
        ; 24-way direction from a's anchor towards b's anchor, as a compass
        ; sector 0-23 clockwise from north on a standard screen (y grows
        ; downward): N=0, NE=3, E=6, SE=9, S=12, SW=15, W=18, NW=21.
        ; Sector centres are exact; band edges are approximated with the
        ; ratio thresholds 1.25, 2.5 and 7.5 (same scheme as the 6502-only
        ; math.direction_qd, but word inputs and the compass wheel).
        ; An 8-way facing can be derived as ((direction_to(a, b) + 1) / 3) & 7.
        ; Coincident anchors return 0 (N) by convention.
        %asm {{
            movem.l d2-d5,-(sp)
            move.w  mobs.Mob.p8v_x_pos(a1),d0
            ext.l   d0
            move.w  mobs.Mob.p8v_x_pos(a0),d2
            ext.l   d2
            sub.l   d2,d0               ; dx = b.x - a.x (long, no wrap)
            move.w  mobs.Mob.p8v_y_pos(a1),d1
            ext.l   d1
            move.w  mobs.Mob.p8v_y_pos(a0),d2
            ext.l   d2
            sub.l   d2,d1               ; dy
            move.l  d0,d2
            bge.s   1$
            neg.l   d2                  ; |dx|
1$:
            move.l  d1,d3
            bge.s   2$
            neg.l   d3                  ; |dy|
2$:
            move.l  d2,d4
            or.l    d3,d4
            bne.s   .bands              ; dx and dy both zero -> convention
            moveq   #0,d0               ; N
            movem.l (sp)+,d2-d5
            rts
.bands:
            moveq   #3,d4               ; quadrant q: 0=NW 1=NE 2=SW 3=SE
            tst.l   d0                  ; NB: d0 still holds dx
            bge.s   3$
            subq    #1,d4
3$:
            tst.l   d1
            bge.s   4$
            subq    #2,d4
4$:
            moveq   #0,d5               ; index = band*8 + axis*4 + q
            cmp.l   d3,d2
            blt.s   5$                  ; |dx| < |dy|: Y axis dominant
            move.l  d2,d0               ; X dominant: swap so d2=S, d3=L
            move.l  d3,d2
            move.l  d0,d3
            bra.s   6$
5$:
            addq    #4,d5
6$:
            ; band via L/S ratios: 5S >= 2L  <=>  L/S < 2.5  -> band 0/1
            move.l  d2,d0
            lsl.l   #2,d0
            add.l   d2,d0               ; 5S
            move.l  d3,d1
            add.l   d1,d1               ; 2L
            cmp.l   d1,d0
            blt.s   7$
            ; band 0/1: 5S >= 4L (L/S <= 1.25) -> band 0; d0 still holds 5S
            move.l  d3,d1
            lsl.l   #2,d1               ; 4L
            cmp.l   d1,d0
            blt.s   10$
            moveq   #0,d1               ; band 0
            bra.s   8$
10$:
            moveq   #1,d1               ; band 1
            bra.s   8$
7$:
            ; band 2/3: 15S >= 2L (L/S < 7.5) -> band 2; recompute 2L in d1
            move.l  d3,d1
            add.l   d1,d1               ; 2L
            move.l  d2,d0
            lsl.l   #4,d0
            sub.l   d2,d0               ; 15S
            cmp.l   d1,d0
            blt.s   11$
            moveq   #2,d1               ; band 2
            bra.s   8$
11$:
            moveq   #3,d1               ; band 3
8$:
            lsl.l   #3,d1               ; band*8
            add.l   d1,d5               ; + axis*4
            add.l   d4,d5               ; + quadrant q
            lea     _mobs_direction24_table(pc),a0
            moveq   #0,d0
            move.b  (a0,d5.l),d0
            movem.l (sp)+,d2-d5
            rts
_mobs_direction24_table:
            dc.b 21,3,15,9              ; band 0, X dominant: near the diagonals
            dc.b 21,3,15,9              ; band 0, Y dominant
            dc.b 20,4,16,8              ; band 1, X dominant
            dc.b 22,2,14,10             ; band 1, Y dominant
            dc.b 19,5,17,7              ; band 2, X dominant
            dc.b 23,1,13,11             ; band 2, Y dominant
            dc.b 18,6,18,6              ; band 3, X dominant: near E/W axes
            dc.b 0,0,12,12              ; band 3, Y dominant: near N/S axes
            ; !notreached!
        }}
    }

    sub union_box(^^mobs.Mob a, ^^mobs.Mob b) -> word, word, word, word {
        ; Smallest box covering both mobs: left, top, right, bottom.
        ; Origin-aware (uses the box accessors); right and bottom are exclusive.
        word l = min(left(a), left(b))
        word t = min(top(a), top(b))
        word r = max(right(a), right(b))
        word bo = max(bottom(a), bottom(b))
        return l, t, r, bo
    }

    sub intersection_box(^^mobs.Mob a, ^^mobs.Mob b) -> word, word, word, word {
        ; Overlap region of two mobs: left, top, right, bottom.
        ; Origin-aware. When the boxes do not overlap the result is an empty
        ; box (right <= left or bottom <= top), so check for that if needed.
        word l = max(left(a), left(b))
        word t = max(top(a), top(b))
        word r = min(right(a), right(b))
        word bo = min(bottom(a), bottom(b))
        return l, t, r, bo
    }
}
