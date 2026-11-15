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
            tst.w   6(a0)
            bne.s   .full
            tst.w   8(a0)
            bne.s   .full
            tst.w   6(a1)
            bne.s   .full
            tst.w   8(a1)
            bne.s   .full
            ; fast path: boxes are [x_pos, x_pos+width) x [y_pos, y_pos+height); d0/d1 only
            move.w  2(a0),d0            ; a.x (= a.left)
            add.w   10(a0),d0           ; a.right
            move.w  2(a1),d1            ; b.x (= b.left)
            cmp.w   d1,d0               ; a.right - b.left
            ble.s   .no
            add.w   10(a1),d1           ; b.right
            cmp.w   2(a0),d1            ; b.right - a.left
            ble.s   .no
            move.w  4(a0),d0            ; a.y (= a.top)
            add.w   12(a0),d0           ; a.bottom
            move.w  4(a1),d1            ; b.y (= b.top)
            cmp.w   d1,d0               ; a.bottom - b.top
            ble.s   .no
            add.w   12(a1),d1           ; b.bottom
            cmp.w   4(a0),d1            ; b.bottom - a.top
            ble.s   .no
            moveq   #0,d0               ; Z set = collision
            rts
.no:
            moveq   #1,d0               ; Z clear = no collision
            rts
.full:
            ; origin-aware path: box = [x_pos+origin, x_pos+origin+size)
            move.w  2(a0),d0            ; a.x
            add.w   6(a0),d0            ; a.left
            add.w   10(a0),d0           ; a.right
            move.w  2(a1),d1            ; b.x
            add.w   6(a1),d1            ; b.left
            cmp.w   d1,d0               ; a.right - b.left
            ble.s   .no
            add.w   10(a1),d1           ; b.right
            move.w  2(a0),d0            ; a.x
            add.w   6(a0),d0            ; a.left
            cmp.w   d0,d1               ; b.right - a.left
            ble.s   .no
            move.w  4(a0),d0            ; a.y
            add.w   8(a0),d0            ; a.top
            add.w   12(a0),d0           ; a.bottom
            move.w  4(a1),d1            ; b.y
            add.w   8(a1),d1            ; b.top
            cmp.w   d1,d0               ; a.bottom - b.top
            ble.s   .no
            add.w   12(a1),d1           ; b.bottom
            move.w  4(a0),d0            ; a.y
            add.w   8(a0),d0            ; a.top
            cmp.w   d0,d1               ; b.bottom - a.top
            ble.s   .no
            moveq   #0,d0               ; Z set = collision
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
            tst.w   6(a0)
            bne.s   .full
            tst.w   8(a0)
            bne.s   .full
            ; fast path: box = [x_pos, x_pos+width) x [y_pos, y_pos+height); d0/d1 only
            sub.w   2(a0),d0            ; px - x_pos
            blt.s   .no
            cmp.w   10(a0),d0           ; (px - x_pos) - width
            bge.s   .no
            sub.w   4(a0),d1            ; py - y_pos
            blt.s   .no
            cmp.w   12(a0),d1           ; (py - y_pos) - height
            bge.s   .no
            moveq   #0,d0               ; Z set = inside
            rts
.no:
            moveq   #1,d0               ; Z clear = outside
            rts
.full:
            sub.w   2(a0),d0            ; px - x_pos
            sub.w   6(a0),d0            ; px - left
            blt.s   .no
            cmp.w   10(a0),d0           ; (px - left) - width
            bge.s   .no
            sub.w   4(a0),d1            ; py - y_pos
            sub.w   8(a0),d1            ; py - top
            blt.s   .no
            cmp.w   12(a0),d1           ; (py - top) - height
            bge.s   .no
            moveq   #0,d0               ; Z set = inside
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
}
