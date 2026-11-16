; sprites - bare-metal OCS/ECS sprite helpers for the Amiga targets.
; Available for both amiga500 and amiga1200.

%import custom
%import mobs

sprites {
    %option merge, no_symbol_prefixing, ignore_unused

    enum Priority {
        PAIRS_0_IN_FRONT = 0,
        PAIRS_1_IN_FRONT = 1,
        PAIRS_2_IN_FRONT = 2,
        PAIRS_3_IN_FRONT = 3,
        PAIRS_4_IN_FRONT = 4
    }

    ; Display-window origin offsets; must track the playfield's DIWSTRT.
    const uword SPRITE_H_OFFSET = 128
    const uword SPRITE_V_OFFSET = 44

    ; Primitives only: init/pos/enable/disable. No state, animation, or
    ; multiplexing. The caller owns sprite data in chip RAM and positions.
    ; Sprite data must start with two control words (POS, CTL) followed by
    ; the bitplane data: one word per bitplane per scanline (2 words/line for
    ; the 4-color OCS format).

    sub init(^^mobs.Sprite spr) {
        if spr.slot < 8
            pokep(&custom.SPR0PT + spr.slot * 4, spr.data)
    }

    sub pos(^^mobs.Sprite spr) {
        ; Restore the hardware pointer each frame (DMA advances it).
        if spr.slot < 8
            pokep(&custom.SPR0PT + spr.slot * 4, spr.data)
        pointer p = spr.data
        uword x = spr.mob.x_pos as uword
        uword y = spr.mob.y_pos as uword
        uword height = spr.mob.height
        ; Convert visible-screen coords to beam coords (OCS display window).
        uword hstart = x + SPRITE_H_OFFSET
        uword vstart = y + SPRITE_V_OFFSET
        uword vstop = vstart + height
        ; POS word.
        p[0] = (vstart & $ff) as ubyte
        p[1] = ((hstart >> 1) & $ff) as ubyte
        ; CTL word with high bits and odd HSTART.
        p[2] = (vstop & $ff) as ubyte
        p[3] = (((vstart & $100) >> 6) | ((vstop & $100) >> 7) | (hstart & 1)) as ubyte
    }

    sub enable() {
        ; Enable sprite DMA and the DMA master.
        custom.DMACON = custom.DMAF_SETCLR | custom.DMAF_MASTER | custom.DMAF_SPRITE
    }

    sub disable() {
        ; Disable sprite DMA without changing the DMA master or other channels.
        custom.DMACON = custom.DMAF_SPRITE
    }

    sub set_priority(ubyte pairs_in_front) {
        ; Set single-playfield BPLCON2 priority using bits 5-3.
        ; NOTE: full-register write; clears PF2PRI (bit 6) and PF1P0-2 (bits 2-0),
        ; so this assumes single-playfield mode. Do not mix with dual-playfield
        ; or Copper-set priority bits.
        if pairs_in_front <= Priority::PAIRS_4_IN_FRONT
            custom.BPLCON2 = pairs_in_front << 3
    }

    sub hide(^^mobs.Sprite spr) {
        ; A zero POS/CTL pair terminates the sprite list.
        pointer p = spr.data
        p[0] = 0
        p[1] = 0
        p[2] = 0
        p[3] = 0
        if spr.slot < 8
            pokep(&custom.SPR0PT + spr.slot * 4, spr.data)
    }

    ; Set the 3 visible colors of a sprite pair (pair 0..3 -> sprites 0&1, 2&3, ...).
    ; Color 0 of the pair stays transparent (background).
    ; Writes 12-bit $0RGB values, correct for OCS/ECS.
    sub set_colors(ubyte pair, uword c1, uword c2, uword c3) {
        if pair > 3
            return
        ^^uword base = &custom.COLOR17 + (pair as uword) * 8
        base[0] = c1
        base[1] = c2
        base[2] = c3
    }
}
