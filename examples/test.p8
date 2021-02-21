%import textio
%zeropage dontuse
%option no_sysinit

main {

;   $1F9C0 - $1F9FF 	PSG registers

    sub start() {
        uword color=0
        repeat {
            cx16.vpoke(1, $fa00+6*2, lsb(color))
            cx16.vpoke(1, $fa01+6*2, msb(color))
            color++
        }
        ;c64.set_rasterirq(&irq.irq, 100, true)

        sys.wait(100)

        ;c64.restore_irq()

;        uword freq = 1181
;        cx16.vpoke(1, $f9c0, lsb(freq))
;        cx16.vpoke(1, $f9c1, msb(freq))
;        cx16.vpoke(1, $f9c2, %11111111)     ; volume
;        cx16.vpoke(1, $f9c3, %11000000)     ; triangle waveform
    }
}


irq {
    uword counter = 0

    sub irq() {

        counter++
    }
}
