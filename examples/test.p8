%import textio
%zeropage basicsafe
%option no_sysinit

main {

;   $1F9C0 - $1F9FF 	PSG registers

    sub start() {

        ubyte xx = '?'
        when xx {
            'a' -> txt.print("a\n")
            'b' -> txt.print("b\n")
            '?' -> {
            }
            else -> txt.print("else\n")
        }

;        uword freq = 1181
;        cx16.vpoke(1, $f9c0, lsb(freq))
;        cx16.vpoke(1, $f9c1, msb(freq))
;        cx16.vpoke(1, $f9c2, %11111111)     ; volume
;        cx16.vpoke(1, $f9c3, %11000000)     ; triangle waveform
    }
}

