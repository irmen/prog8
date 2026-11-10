; copper - bare-metal copper list builder, shared by the amiga500 and amiga1200 targets.
;
; Kickstart 1.3 compatible (uses no OS calls at all).
;
; Emits raw copper words into a caller-owned CHIP-RAM buffer (AllocMem with
; MEMF_CHIP, or a %option amiga_chipram block). No OS calls are used, so this
; works both under the OS and after custom.grab_system(); it is the bare-metal
; counterpart of graphics.CMove/CWait, which need the OS.
;
; The pattern is patch-don't-rebuild: move() and friends return the indices of
; the emitted value words, so the main loop updates colors, scroll values or
; plane pointers in place every frame. See playfield.patch_copper() for the
; plane-pointer half of this (copper.attach_planes() emits the slots it fills).
; DMA setup stays at the call site: point custom.COP1LC at the list, set the
; DMACON bits you need, strobe custom.COPJMP1.
;
; Single active list (module-level cursor, like playfield). Emissions that do
; not fit are dropped and counted in overflows.

%import custom

copper {
    %option merge, ignore_unused

    ^^uword list          ; caller-owned word buffer (must be CHIP RAM)
    uword cursor          ; next free word index
    uword capacity        ; buffer size in words
    uword overflows       ; dropped emissions (buffer full); reset by init()

    sub init(^^uword buffer, uword words) {
        list = buffer
        cursor = 0
        capacity = words
        overflows = 0
    }

    sub move(uword reg, uword value) -> uword {
        ; Emit a copper MOVE (register offset from $dff000, e.g. $0180 for
        ; COLOR00). Returns the index of the value word for per-frame patching,
        ; or $ffff when the list is full (counted in overflows).
        if cursor + 2 > capacity {
            overflows++
            return $ffff
        }
        uword slot = cursor + 1
        list[cursor] = reg & $1fe      ; register offsets are even (bit 0 = MOVE)
        list[slot] = value
        cursor += 2
        return slot
    }

    sub wait(ubyte v, ubyte h) {
        ; Emit a copper WAIT for beam position (v, h): exact-position wait,
        ; no blitter wait. h is the raw copper HP value ($07 is the classic
        ; just-after-horizontal-blank used to hit the left edge).
        if cursor + 2 > capacity {
            overflows++
            return
        }
        list[cursor] = ((v as uword) << 8) | ((h as uword) << 1) | 1
        list[cursor+1] = $fffe
        cursor += 2
    }

    sub skip(ubyte v, ubyte h) {
        ; Emit a copper SKIP for beam position (v, h): the next instruction is
        ; skipped when the beam has passed (v, h).
        if cursor + 2 > capacity {
            overflows++
            return
        }
        list[cursor] = ((v as uword) << 8) | ((h as uword) << 1) | 1
        list[cursor+1] = $ffff
        cursor += 2
    }

    sub end() {
        ; Terminate the list ($ffff, $fffe): the copper restarts from COP1LC.
        if cursor + 2 > capacity {
            overflows++
            return
        }
        list[cursor] = $ffff
        list[cursor+1] = $fffe
        cursor += 2
    }

    sub set(uword slot, uword value) {
        ; Write value into a previously emitted slot (from move(),
        ; attach_planes(), rasterbar(), ...). This is the per-frame half of
        ; patch-don't-rebuild: build once, set() every frame.
        ; The slot holds one raw word, so OCS/AGA palette differences are in
        ; what you store, not in this call:
        ; - OCS/ECS (or AGA used at 12-bit): one MOVE per color, so one slot
        ;   per color: slot = copper.move($0180, 0), then per frame
        ;   copper.set(slot, custom.pack12($ff0000)).
        ; - Full 24-bit AGA color needs the BPLCON3 bank dance (bank select,
        ;   high nibbles, LOCT bank select, low nibbles): emit it as four
        ;   move()s and set() each slot every frame, same as
        ;   custom.set_aga_color() does for direct writes.
        ; Out-of-range slots ($ffff from a failed move, or past capacity)
        ; are dropped and counted in overflows.
        if slot >= capacity {
            overflows++
            return
        }
        list[slot] = value
    }

    sub base_list(^^custom.DisplayRegs display, ubyte depth) -> uword {
        ; Emit the common copper-list prefix for a compute_display() mode:
        ; empty BPLxPT MOVE slots for depth planes, then the shadow
        ; display registers (FMODE, BPLCON0, DIWSTRT/STOP, DDFSTRT/STOP,
        ; BPL1MOD/2, DIWHIGH, plus BPLCON1/2 reset to 0 for no fine
        ; scroll and single-playfield priorities). Returns the base
        ; value-word index of the plane slots, ready to pass to
        ; playfield.patch_copper() or copper.set_planes().
        ; Returns $ffff on a null display pointer, invalid depth, or a
        ; full list (partially emitted).
        ;
        ; display is the pointer returned by custom.compute_display();
        ; select the mode there with the DISPLAY_ flags (DISPLAY_DEFAULT
        ; for lores, | DISPLAY_HIRES for 640px, | DISPLAY_EHB or
        ; | DISPLAY_HAM for the 6-plane modes). Assumes copper.init()
        ; was already called on a CHIP-RAM buffer. DMA setup stays at
        ; the call site (emit your own DMACON MOVE before calling this),
        ; and colors/extras stay after: the caller adds COLORxx MOVEs,
        ; rasterbars or WAITS, then copper.end().
        if display == 0
            return $ffff
        uword base = attach_planes(depth)
        if base == $ffff
            return $ffff
        void move($01fc, display.fmode)
        void move($0100, display.bplcon0)
        void move($008e, display.diwstrt)
        void move($0090, display.diwstop)
        void move($0092, display.ddfstrt)
        void move($0094, display.ddfstop)
        void move($0108, display.bpl1mod)
        void move($010a, display.bpl2mod)
        void move($0102, 0)        ; BPLCON1: reset fine scrolling
        void move($01e4, display.diwhigh)
        void move($0104, 0)        ; BPLCON2: single-playfield priorities
        return base
    }

    sub attach_planes(ubyte depth) -> uword {        ; Emit empty BPLxPT MOVE slots for depth planes and return the base
        ; value-word index, ready to pass to playfield.patch_copper().
        ; Returns $ffff on invalid depth or a full list (partially emitted).
        if depth == 0 or depth > 8
            return $ffff
        uword base = cursor + 1
        for p in 0 to depth-1 {
            uword reg = $00e0 + (p as uword)*4
            if move(reg, 0) == $ffff
                return $ffff
            if move(reg+2, 0) == $ffff
                return $ffff
        }
        return base
    }

    sub set_planes(uword base, ^^pointer planes, ubyte depth) {
        ; Patch plane addresses into the MOVE value slots emitted by
        ; attach_planes(): plane p's high word goes to slot base + p*4,
        ; low word to slot base+2 + p*4 (same layout as
        ; playfield.patch_copper(), but without the graphics.BitMap).
        ; This is the visible half of a bare-metal double-buffer flip:
        ; build the list once, then set_planes() with the other buffer's
        ; plane table each frame. planes is an array of 32-bit plane
        ; addresses (same layout as graphics.BitMap.Planes; cast a
        ; pointer array with `as ^^long`). depth is capped at 8.
        if depth > 8
            depth = 8
        for p in 0 to depth-1 {
            uword slot = base + (p as uword)*4
            set(slot, msw(planes[p]))
            set(slot+2, lsw(planes[p]))
        }
    }

    sub rasterbar(ubyte vstart, ubyte lines) -> uword {
        ; Emit a WAIT + MOVE COLOR00 skeleton for lines scanlines starting at
        ; vstart (hp $07, colors zeroed) and return the first color slot;
        ; subsequent colors live 4 words apart (WAIT pair + MOVE pair each).
        ; The caller writes actual colors into the slots every frame.
        ; Returns $ffff when lines is 0 or the list fills up (partially emitted).
        if lines == 0
            return $ffff
        uword first = cursor + 3
        for i in 0 to lines-1 {
            wait(vstart + i, 7)
            if move($0180, 0) == $ffff
                return $ffff
        }
        return first
    }

    sub aga_move(ubyte index) -> uword {
        ; Emit the full four-MOVE sequence for one 24-bit AGA color (BPLCON3
        ; bank select, COLORxx high nibbles, BPLCON3 LOCT bank select,
        ; COLORxx low nibbles), all zeroed. Returns the base value-word
        ; index: the other three values live at base+2, base+4, base+6, so a
        ; single variable per color is enough — aga_set() does the rest.
        ; Returns $ffff when the list fills up (nothing emitted).
        if cursor + 8 > capacity {
            overflows++
            return $ffff
        }
        uword reg = $0180 + ((index as uword) & $1f)*2
        uword base = cursor + 1
        void move($0106, 0)
        void move(reg, 0)
        void move($0106, 0)
        void move(reg, 0)
        return base
    }

    sub aga_set(uword base, ubyte index, long rgb24) {
        ; Rewrite one aga_move() color in place: bank words plus the nibble
        ; split, same math as custom.set_aga_color() but into list slots.
        if base == $ffff
            return
        uword bank = ((index as uword) >> 5) << 13
        uword rl = ((rgb24 >> 16) as uword) & $000f
        uword gl = ((rgb24 >> 8) as uword) & $000f
        uword bl = (rgb24 as uword) & $000f
        set(base, bank)
        set(base+2, custom.pack12(rgb24))
        set(base+4, bank | $0200)
        set(base+6, (rl << 8) | (gl << 4) | bl)
    }
}
