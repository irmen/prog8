%import audio
%import textio

; Boulder Dash menu theme for Amiga500
; Uses the high-level audio interface for stereo playback.
; Music by Peter Liepa (1984).
; Note data and SID frequencies from:
;   https://www.elmerproductions.com/sp/peterb/sounds.html

main {
    ; 128 note pairs: lsb = voice 1 (bass, left), msb = voice 2 (melody, right)
    uword[] melody = [
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

    ; SID note index to sample playback rate for the 40-byte, one-cycle waveform.
    uword[] sample_rates = [
        2385, 2541, 2683, 2853, 3019, 3200, 3390, 3570,
        3795, 4029, 4273, 4526, 4770, 5082, 5365, 5707,
        6038, 6399, 6780, 7141, 7589, 8058, 8545, 9053,
        9541, 10165, 10731, 11414, 12077, 12799, 13560, 14282,
        15179, 16115, 17091, 18106, 19081, 20330, 21461, 22827,
        24154, 25597, 27119, 28563
    ]

    sub start() {
        txt.print("\nBoulder Dash C64 - menu theme\n")

        if audio.init() {

            for note in melody {
                ubyte left = lsb(note)
                ubyte right = msb(note)
                uword left_rate = sample_rates[left-15]
                uword right_rate = sample_rates[right-15]
                uword left_cycles = left_rate / 256
                uword right_cycles = right_rate / 256
                audio.play(0, sample.data, len(sample.data), left_rate, 64, left_cycles)
                audio.play(1, sample.data, len(sample.data), right_rate, 64, right_cycles)

                txt.chrout('.')

                audio.wait_channel(0)
                audio.wait_channel(1)
            }

            txt.print("\ndone\n")

            audio.closedown()

        } else {
            txt.print("audio init failed!\n")
        }
    }
}

sample {
    %option amiga_chipram

    ; 40-byte triangle waveform containing one complete cycle.
    byte[] data = [
        0,13,25,38,51,64,76,89,102,114,
        127,114,102,89,76,63,50,38,25,12,
        0,-13,-26,-39,-52,-64,-77,-90,-102,-115,
        -128,-115,-102,-90,-77,-64,-51,-38,-26,0
    ]
}
