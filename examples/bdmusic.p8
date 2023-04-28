%import textio
%import syslib

main {

sub start() {

    const ubyte waveform = %0001       ; triangle

    c64.AD1 = %00011010
    c64.SR1 = %00000000
    c64.AD2 = %00011010
    c64.SR2 = %00000000
    c64.MVOL = 15

    txt.print("will play the music from boulderdash,\nmade in 1984 by peter liepa.\npress enter to start: ")
    void cbm.CHRIN()
    txt.clear_screen()

    repeat {
        uword note
        for note in notes {
            ubyte note1 = lsb(note)
            ubyte note2 = msb(note)
            c64.FREQ1 = music_freq_table[note1]    ; set lo+hi freq of voice 1
            c64.FREQ2 = music_freq_table[note2]    ; set lo+hi freq of voice 2

            ; retrigger voice 1 and 2 ADSR
            c64.CR1 = waveform <<4 | 0
            c64.CR2 = waveform <<4 | 0
            c64.CR1 = waveform <<4 | 1
            c64.CR2 = waveform <<4 | 1

            print_notes(note1, note2)
            sys.wait(8)
        }
    }
}

sub print_notes(ubyte n1, ubyte n2) {
    txt.nl()
    txt.plot(n1/2, 24)
    txt.color(7)
    txt.chrout('Q')
    txt.plot(n2/2, 24)
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


    uword[] music_freq_table = [
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        732, 778, 826, 876, 928, 978, 1042, 1100, 1170, 1238, 1312, 1390, 1464, 1556,
        1652, 1752, 1856, 1956, 2084, 2200, 2340, 2476, 2624, 2780, 2928, 3112, 3304,
        3504, 3712, 3912, 4168, 4400, 4680, 4952, 5248, 5560, 5856, 6224, 6608, 7008,
        7424, 7824, 8336, 8800, 9360, 9904, 10496, 11120, 11712
    ]

}
