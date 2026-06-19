%import textio
%import petsnd
%option no_sysinit
%zeropage basicsafe

; Plays the song "Ode an die Freude" (Beethoven)
; Uses petsnd.song() + petsnd.update() driven by an interrupt handler routine.

main {
    sub start() {
        txt.print("\node an die freude - beethoven\n(playing using irq)\n")

        petsnd.on()
        petsnd.set_gap(1)       ; default intra note gap is 1 tick
        petsnd.song(&song.notes, &song.durations, len(song.notes))

        sys.set_irq(&petsnd.update)

        do {
            sys.waitvsync()
            txt.chrout('.')
        } until not petsnd.is_playing()

        petsnd.off()
        sys.restore_irq()
        txt.print("\ndone\n")
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
    const ubyte tempo = 180     ; beats per minute
    const ubyte beat = 3600 / tempo as uword

    const ubyte q = beat         ; quarter note gets the beat (♩ = 120)
    const ubyte w = 4 * beat     ; whole note
    const ubyte h = 2 * beat     ; half note
    const ubyte d = 3 * beat / 2 ; dotted quarter note
    const ubyte e = beat / 2     ; eighth note

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
