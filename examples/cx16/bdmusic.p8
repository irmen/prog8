%import textio
%import syslib
%import psg

main {

    sub explosion() {
        ; this subroutine is not used but it is an example of how to make a sound effect using the psg library!
        psg.silent()
        cx16.enable_irq_handlers(true)
        cx16.set_vsync_irq_handler(&psg.envelopes_irq)
        psg.voice(0, psg.LEFT, 0, psg.NOISE, 0)
        psg.voice(1, psg.RIGHT, 0, psg.NOISE, 0)
        psg.freq(0, 1000)
        psg.freq(1, 2000)
        psg.envelope(0, 63, 50, 0, 5)
        psg.envelope(1, 63, 80, 0, 6)
        sys.wait(100)
        psg.silent()
        cx16.disable_irq_handlers()
    }

    sub sweeping() {
        ; this subroutine is not used but it is an example of how to make a sound effect using the psg library!
        psg.silent()
        psg.voice(0, psg.LEFT, 63, psg.PULSE, 0)
        psg.voice(1, psg.LEFT, 63, psg.PULSE, 1)
        psg.voice(2, psg.RIGHT, 63, psg.PULSE, 2)
        psg.voice(3, psg.RIGHT, 63, psg.PULSE, 3)
        psg.freq(0, 160)
        psg.freq(1, 161)
        psg.freq(2, 162)
        psg.freq(3, 163)
        repeat {
            ubyte pw
            for pw in 0 to 63 {
                psg.pulse_width(0, pw)
                psg.pulse_width(1, pw)
                psg.pulse_width(2, pw)
                psg.pulse_width(3, pw)
                sys.wait(2)
            }
            for pw in 62 downto 1 {
                psg.pulse_width(0, pw)
                psg.pulse_width(1, pw)
                psg.pulse_width(2, pw)
                psg.pulse_width(3, pw)
                sys.wait(2)
            }
        }
        psg.silent()
    }


    sub start() {
        txt.print("will play the music from boulderdash,\nmade in 1984 by peter liepa.\npress enter to start: ")
        void cbm.CHRIN()
        txt.clear_screen()

        psg.silent()
        psg.voice(0, psg.LEFT, 63, psg.TRIANGLE, 0)
        psg.voice(1, psg.RIGHT, 63, psg.TRIANGLE, 0)

        cx16.enable_irq_handlers(true)
        cx16.set_vsync_irq_handler(&psg.envelopes_irq)

        repeat {
            uword note
            for note in notes {
                ubyte note0 = lsb(note)
                ubyte note1 = msb(note)
                psg.freq(0, vera_freqs[note0])
                psg.freq(1, vera_freqs[note1])
                psg.envelope(0, 63, 255, 0, 6)
                psg.envelope(1, 63, 255, 0, 6)
                print_notes(note0, note1)
                sys.wait(10)
            }
        }

        psg.silent()
        cx16.disable_irq_handlers()
    }

    sub print_notes(ubyte n1, ubyte n2) {
        txt.nl()
        txt.plot(n1, txt.DEFAULT_HEIGHT-1)
        txt.color(7)
        txt.chrout('Q')
        txt.plot(n2, txt.DEFAULT_HEIGHT-1)
        txt.color(4)
        txt.chrout('Q')
    }


    ; details about the boulderdash music can be found here:
    ; https://www.elmerproductions.com/sp/peterb/sounds.html#Theme%20tune

    uword[] notes = [
        $1622, $1d26, $2229, $252e, $1424, $1f27, $2029, $2730,
        $122a, $122c, $1e2e, $1231, $202c, $3337, $212d, $3135,
        $1622, $162e, $161d, $1624, $1420, $1430, $1424, $1420,
        $1622, $162e, $161d, $1624, $1e2a, $1e3a, $1e2e, $1e2a,
        $142c, $142c, $141b, $1422, $1c28, $1c38, $1c2c, $1c28,
        $111d, $292d, $111f, $292e, $0f27, $0f27, $1633, $1627,
        $162e, $162e, $162e, $162e, $222e, $222e, $162e, $162e,
        $142e, $142e, $142e, $142e, $202e, $202e, $142e, $142e,
        $162e, $322e, $162e, $332e, $222e, $322e, $162e, $332e,
        $142e, $322e, $142e, $332e, $202c, $302c, $142c, $312c,
        $162e, $163a, $162e, $3538, $222e, $2237, $162e, $3135,
        $142c, $1438, $142c, $1438, $202c, $2033, $142c, $1438,
        $162e, $322e, $162e, $332e, $222e, $322e, $162e, $332e,
        $142e, $322e, $142e, $332e, $202c, $302c, $142c, $312c,
        $2e32, $292e, $2629, $2226, $2c30, $272c, $2427, $1420,
        $3532, $322e, $2e29, $2926, $2730, $242c, $2027, $1420
    ]

    uword[] vera_freqs = [
        0,0,0,0,0,0,0,0,0,0,   ; first 10 notes are not used
        120, 127, 135, 143, 152, 160, 170, 180, 191, 203,
        215, 227, 240, 255, 270, 287, 304, 320, 341, 360,
        383, 405, 429, 455, 479, 509, 541, 573, 607, 640,
        682, 720, 766, 810, 859, 910, 958, 1019, 1082, 1147,
        1215, 1280, 1364, 1440, 1532, 1621, 1718, 1820, 1917]

}
