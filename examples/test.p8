%import textio
%zeropage dontuse
%option no_sysinit

main {

;   $1F9C0 - $1F9FF 	PSG registers

    sub init(uword addr, ubyte length) {
        @(addr+length) = $ea
    }

    sub start() {

        init($4000, 0)
        txt.print_uwhex(@($4000), true)

;        uword freq = 1181
;        cx16.vpoke(1, $f9c0, lsb(freq))
;        cx16.vpoke(1, $f9c1, msb(freq))
;        cx16.vpoke(1, $f9c2, %11111111)     ; volume
;        cx16.vpoke(1, $f9c3, %11000000)     ; triangle waveform
    }
}

