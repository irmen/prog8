%import textio
%import syslib
%import floats

main {

sub start() {

    txt.print("will play the music from boulderdash,\nmade in 1984 by peter liepa.\npress enter to start: ")
    void c64.CHRIN()
    txt.clear_screen()

    repeat {
        uword note
        for note in notes {
            ubyte note1 = lsb(note)
            ubyte note2 = msb(note)
            uword freqR = freq(note1)
            uword freqL = freq(note2)
            cx16.vpoke(1, $F9C0, lsb(freqR))
            cx16.vpoke(1, $F9C1, msb(freqR))
            cx16.vpoke(1, $F9C2, %10111111)     ; left, max volume
            cx16.vpoke(1, $F9C3, %10000000)     ; triangle
            cx16.vpoke(1, $F9C4, lsb(freqL))
            cx16.vpoke(1, $F9C5, msb(freqL))
            cx16.vpoke(1, $F9C6, %01111111)     ; right, max volume
            cx16.vpoke(1, $F9C7, %10000000)     ; triangle

            ; TODO ADSR of some kind?

            print_notes(note1, note2)
            sys.wait(10)
        }
    }
}

sub freq(ubyte note) -> uword {
    float fword = freqs_hz[note-10] / (48828.125 / 131072.0)       ; formula from the Vera PSG docs
    return fword as uword
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

    float[] freqs_hz = [
            ; first 10 are unused so should index by i-10
         44.6,
         47.4,
         50.4,
         53.4,
         56.6,
         59.6,
         63.5,
         67.1,
         71.3,
         75.5,
         80.0,
         84.7,
         89.3,
         94.9,
        100.7,
        106.8,
        113.2,
        119.3,
        127.1,
        134.1,
        142.7,
        151.0,
        160.0,
        169.5,
        178.5,
        189.7,
        201.4,
        213.6,
        226.3,
        238.5,
        254.1,
        268.3,
        285.3,
        301.9,
        320.0,
        339.0,
        357.0,
        379.5,
        402.9,
        427.3,
        452.6,
        477.0,
        508.2,
        536.5,
        570.7,
        603.8,
        639.9,
        678.0,
        714.1
    ]
}
