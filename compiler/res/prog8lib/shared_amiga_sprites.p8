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

    ; AGA-only helpers follow. aga_set_width() must be called first.
    enum AgaWidth { W16 = 0, W32 = 1, W64 = 3 }

    private uword aga_n = 1   ; 1, 2 or 4 = width/16; set by aga_set_width
    private uword aga_spres   ; shadow of BPLCON3's SPRES bits; the register is
                              ; write-only, so reads cannot be used to preserve them
    ; Sprite data must start with two control words (POS, CTL) followed by
    ; the bitplane data: one word per bitplane per scanline (2 words/line for
    ; the 4-color OCS format).

    sub init(^^mobs.Sprite spr) {
        if spr.slot < 8
            pokep(&custom.SPR0PT + spr.slot * 4, spr.data)
    }

    sub pos(^^mobs.Sprite spr) {
        ; Call every displayed frame, even if the position is unchanged. Sprite DMA
        ; advances SPRxPT through the data list as it reads, so the start pointer
        ; must be reloaded here or the sprite vanishes after one frame.
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
        ; Preserve the attach bit (bit 7) so attached (16-color) sprites keep it.
        p[3] = ((((vstart & $100) >> 6) | ((vstop & $100) >> 7) | (hstart & 1)) | (p[3] & $80)) as ubyte
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
    ; On AGA, COLORxx writes go through the BPLCON3 bank/LOCT window, so
    ; select bank 0/LOCT=0 first; otherwise a leftover Workbench bank would
    ; land the colors in the wrong 32-color slice. BPLCON3 is write-only
    ; (reads return undefined values), so the value is composed from the
    ; module's SPRES shadow instead of reading the register back.
    sub set_colors(ubyte pair, uword c1, uword c2, uword c3) {
        if pair > 3
            return
        if custom.detect_chipset()==custom.CHIPSET::AGA {
            custom.BPLCON3 = aga_spres << 6
        }
        ^^uword base = &custom.COLOR17 + (pair as uword) * 8
        base[0] = c1
        base[1] = c2
        base[2] = c3
    }

    ; Set all 16 colors of attached (16-color) sprites.
    ; colors points to 16 uwords -> COLOR16..31 (the single shared sprite bank).
    ; On AGA, COLORxx writes go through the BPLCON3 bank/LOCT window, so
    ; select bank 0/LOCT=0 first; otherwise a leftover Workbench bank would
    ; land the colors in the wrong 32-color slice. As in set_colors(), the
    ; write-only BPLCON3 is composed from the SPRES shadow.
    sub set_colors16(^^uword colors) {
        if custom.detect_chipset()==custom.CHIPSET::AGA {
            custom.BPLCON3 = aga_spres << 6
        }
        ^^uword base = &custom.COLOR16
        for i in 0 to 15 {
            base[i] = colors[i]
        }
    }

    ; Load 16 attached-sprite colors from $00RRGGBB values.
    sub aga_set_colors16(^^long colors) {
        for i in 0 to 15
            custom.set_color((i + 16) as ubyte, colors[i])
    }

    ; Attach/detach an odd sprite to the preceding even sprite (16-color mode).
    ; Width-aware: the CTL word lives at aga_n*2 bytes (2 for 16px, 4 for
    ; 32px, 8 for 64px); aga_n defaults to 1 so OCS 16px uses byte 3.
    ; Must be called after init(); pos() preserves the bit each frame.
    sub attach(^^mobs.Sprite odd_spr, bool on) {
        if odd_spr.slot < 8 {
            pointer p = odd_spr.data
            uword off = aga_n * 2
            if off == 0
                off = 2
            if on
                p[off+1] = p[off+1] | $80
            else
                p[off+1] = p[off+1] & $7f
        }
    }

    ; Initialize an attached (16-color) sprite pair. even_spr.slot must be set;
    ; the odd sprite gets the following slot. Attaches, inits and positions both.
    sub init_attached(^^mobs.Sprite even_spr, ^^mobs.Sprite odd_spr) {
        odd_spr.slot = even_spr.slot + 1
        attach(odd_spr, true)
        init(even_spr)
        init(odd_spr)
        pos(even_spr)
        pos(odd_spr)
    }

    ; Position both sprites of an attached pair.
    sub pos_attached(^^mobs.Sprite even_spr, ^^mobs.Sprite odd_spr) {
        pos(even_spr)
        pos(odd_spr)
    }

    ; AGA-only: set sprite fetch/display width globally via FMODE bits 3-2.
    ; aga_set_width() must be called before any aga_* sprite use.
    sub aga_set_width(ubyte width) {
        custom.FMODE = (custom.FMODE & $fff3) | ((width as uword & 3) << 2)
        if width == 0
            aga_n = 1
        else if width == 3
            aga_n = 4
        else
            aga_n = 2
    }

    ; AGA-only: pointer setup like init(), but width-specific header.
    ; aga_set_width() must be called first.
    sub aga_init(^^mobs.Sprite spr) {
        if spr.slot < 8
            pokep(&custom.SPR0PT + spr.slot * 4, spr.data)
    }

    ; AGA-only: wide-aware replacement for pos().
    ; aga_set_width() must be called first.
    sub aga_pos(^^mobs.Sprite spr) {
        ; Call every displayed frame, even if the position is unchanged. Sprite DMA
        ; advances SPRxPT through the data list as it reads, so the start pointer
        ; must be reloaded here or the sprite vanishes after one frame.
        if spr.slot < 8
            pokep(&custom.SPR0PT + spr.slot * 4, spr.data)
        pointer p = spr.data
        uword x = spr.mob.x_pos as uword
        uword y = spr.mob.y_pos as uword
        uword height = spr.mob.height
        uword hstart = x + SPRITE_H_OFFSET
        uword vstart = y + SPRITE_V_OFFSET
        uword vstop = vstart + height
        p[0] = (vstart & $ff) as ubyte
        p[1] = ((hstart >> 1) & $ff) as ubyte
        uword off = aga_n * 2
        p[off] = (vstop & $ff) as ubyte
        p[off+1] = ((((vstart & $100) >> 6) | ((vstop & $100) >> 7) |
                     (hstart & 1)) | (p[off+1] & $80)) as ubyte
    }

    ; AGA-only: zero the header (aga_n*4 bytes) and reload the pointer.
    ; aga_set_width() must be called first.
    sub aga_hide(^^mobs.Sprite spr) {
        pointer p = spr.data
        uword n = aga_n * 4
        for i in 0 to n-1 {
            p[i] = 0
        }
        if spr.slot < 8
            pokep(&custom.SPR0PT + spr.slot * 4, spr.data)
    }

    ; AGA-only: initialize an attached (16/256-color) sprite pair.
    ; aga_set_width() must be called first.
    sub aga_init_attached(^^mobs.Sprite even_spr, ^^mobs.Sprite odd_spr) {
        odd_spr.slot = even_spr.slot + 1
        attach(odd_spr, true)
        aga_init(even_spr)
        aga_init(odd_spr)
        aga_pos(even_spr)
        aga_pos(odd_spr)
    }

    ; AGA-only: position both sprites of an attached pair.
    ; aga_set_width() must be called first.
    sub aga_pos_attached(^^mobs.Sprite even_spr, ^^mobs.Sprite odd_spr) {
        aga_pos(even_spr)
        aga_pos(odd_spr)
    }

    ; AGA-only: sprite resolution via BPLCON3 (write-only register: the value
    ; is composed from the module's SPRES shadow, no read-back).
    ; aga_set_width() must be called first.
    sub aga_set_resolution(ubyte res) {
        aga_spres = res & 3
        custom.BPLCON3 = aga_spres << 6
    }

    ; AGA-only: BPLCON4 palette banks for even/odd sprites. BPLCON4 is
    ; write-only, so this is a full-register write: BPLAM (the bitplane
    ; color XOR mask in bits 15-8) is cleared.
    ; aga_set_width() must be called first.
    sub aga_set_bank(ubyte even_bank, ubyte odd_bank) {
        custom.BPLCON4 = ((even_bank & $0f) << 4) | (odd_bank & $0f)
    }
}
