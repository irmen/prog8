%import textio
%import petsnd
%option no_sysinit
%zeropage basicsafe

; Plays the song "Ode an die Freude" (Beethoven)
; Uses petsnd.song() + petsnd.update() driven by an interrupt handler routine.

main {
    uword old_irq_vector

    sub start() {
        txt.print("\node an die freude - beethoven\n(playing using irq)\n")

        petsnd.on()
        petsnd.set_gap(1)       ; default intra note gap is 1 tick
        petsnd.song(&song.notes, &song.durations, len(song.notes))

        sys.set_irqd()
        old_irq_vector = cbm.CINV
        cbm.CINV = &irq_handler
        sys.clear_irqd()

        do {
            sys.waitvsync()
            txt.chrout('.')
        } until not petsnd.is_playing()

        petsnd.off()

        sys.set_irqd()
        cbm.CINV = old_irq_vector
        sys.clear_irqd()
        txt.print("\ndone\n")
    }

    ; Bare CINV IRQ handler - called on each vsync tick.
    ; Drives petsnd sequencer, then chains to the system's default IRQ handler.
    sub irq_handler() {
        sys.save_prog8_internals()
        void petsnd.update()
        sys.restore_prog8_internals()
        goto old_irq_vector
    }
}

song {
    ubyte[] notes = [
        ; ---- A section ----
        petsnd.E_5, petsnd.E_5, petsnd.F_5, petsnd.G_5,
        petsnd.G_5, petsnd.F_5, petsnd.E_5, petsnd.D_5,
        petsnd.C_5, petsnd.C_5, petsnd.D_5, petsnd.E_5,
        petsnd.E_5, petsnd.D_5, petsnd.D_5,
        petsnd.REST,

        ; ---- A section repeat ----
        petsnd.E_5, petsnd.E_5, petsnd.F_5, petsnd.G_5,
        petsnd.G_5, petsnd.F_5, petsnd.E_5, petsnd.D_5,
        petsnd.C_5, petsnd.C_5, petsnd.D_5, petsnd.E_5,
        petsnd.D_5, petsnd.C_5, petsnd.C_5,
        petsnd.REST,

        ; ---- B section (bridge) ----
        petsnd.D_5, petsnd.D_5, petsnd.E_5, petsnd.C_5,
        petsnd.D_5, petsnd.E_5, petsnd.F_5, petsnd.E_5,
        petsnd.C_5, petsnd.D_5, petsnd.E_5, petsnd.F_5,
        petsnd.E_5, petsnd.D_5, petsnd.C_5, petsnd.D_5,
        petsnd.G_4,

        ; ---- A section final ----
        petsnd.E_5, petsnd.E_5, petsnd.F_5, petsnd.G_5,
        petsnd.G_5, petsnd.F_5, petsnd.E_5, petsnd.D_5,
        petsnd.C_5, petsnd.C_5, petsnd.D_5, petsnd.E_5,
        petsnd.D_5, petsnd.C_5, petsnd.C_5
    ]

    ; durations are total ticks per note slot (note-on + silence gap).
    ; the gap is configured via set_gap() (here 1 tick).
    ; note-on time = duration - gap, computed automatically.
    const ubyte w = 64      ; whole note (~1.07s)
    const ubyte h = 32      ; half note (~0.53s)
    const ubyte d = 24      ; dotted quarter (~0.40s)
    const ubyte q = 16      ; quarter note (~0.27s)
    const ubyte e = 8       ; eighth note (~0.13s)

    ubyte[] durations = [
        ; ---- A section ----
        q, q, q, q,
        q, q, q, q,
        q, q, q, q,
        d, e, h,
        e,

        ; ---- A section repeat ----
        q, q, q, q,
        q, q, q, q,
        q, q, q, q,
        d, e, h,
        e,

        ; ---- B section (bridge) ----
        q, q, q, q,         ; bar 1: D D E C - straight quarters
        q, e, e, q,         ; bar 2: D E F E
        q, q, e, e,         ; bar 3: C D E F
        q, q, q, q,         ; bar 4: E D C D
        h,                  ; bar 5: G - half note resolution

        ; ---- A section final ----
        q, q, q, q,
        q, q, q, q,
        q, q, q, q,
        d, e, h
    ]
}
