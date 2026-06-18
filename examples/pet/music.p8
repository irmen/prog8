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
        petsnd.E_4, petsnd.E_4, petsnd.F_4, petsnd.G_4,
        petsnd.G_4, petsnd.F_4, petsnd.E_4, petsnd.D_4,
        petsnd.C_4, petsnd.C_4, petsnd.D_4, petsnd.E_4,
        petsnd.E_4, petsnd.D_4, petsnd.D_4,

        ; ---- A section repeat ----
        petsnd.E_4, petsnd.E_4, petsnd.F_4, petsnd.G_4,
        petsnd.G_4, petsnd.F_4, petsnd.E_4, petsnd.D_4,
        petsnd.C_4, petsnd.C_4, petsnd.D_4, petsnd.E_4,
        petsnd.D_4, petsnd.C_4, petsnd.C_4,

        ; ---- B section (bridge) ----
        petsnd.D_4, petsnd.D_4, petsnd.E_4, petsnd.C_4,
        petsnd.D_4, petsnd.E_4, petsnd.F_4, petsnd.E_4,
        petsnd.C_4, petsnd.D_4, petsnd.E_4, petsnd.F_4,
        petsnd.E_4, petsnd.D_4, petsnd.C_4, petsnd.D_4,
        petsnd.G_4,

        ; ---- A section final ----
        petsnd.E_4, petsnd.E_4, petsnd.F_4, petsnd.G_4,
        petsnd.G_4, petsnd.F_4, petsnd.E_4, petsnd.D_4,
        petsnd.C_4, petsnd.C_4, petsnd.D_4, petsnd.E_4,
        petsnd.D_4, petsnd.C_4, petsnd.C_4
    ]

    ; durations (in jiffy ticks at 60Hz = 1/60 second per tick)
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

        ; ---- A section repeat ----
        q, q, q, q,
        q, q, q, q,
        q, q, q, q,
        d, e, w,

        ; ---- B section (bridge) ----
        q, q, q, q,
        e, e, e, e,
        q, e, e, e,
        e, q, q, q,
        q,

        ; ---- A section final ----
        q, q, q, q,
        q, q, q, q,
        q, q, q, q,
        d, e, h
    ]
}
