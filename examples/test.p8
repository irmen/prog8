%import textio
%zeropage dontuse
%option no_sysinit

main {

;   $1F9C0 - $1F9FF 	PSG registers

    sub start() {
        cx16.set_rasterirq(&irq.irq, 100)
        ;cx16.set_irq(&irq.irq, true)
        sys.wait(100)
        cx16.restore_irq()

;        uword freq = 1181
;        cx16.vpoke(1, $f9c0, lsb(freq))
;        cx16.vpoke(1, $f9c1, msb(freq))
;        cx16.vpoke(1, $f9c2, %11111111)     ; volume
;        cx16.vpoke(1, $f9c3, %11000000)     ; triangle waveform
    }
}


irq {
    %option force_output

    uword counter = 0

    sub irq() {
        cx16.vpoke(1, $fa00+6*2, lsb(counter))
        cx16.vpoke(1, $fa01+6*2, msb(counter))
        repeat 20 {
            uword xx
            repeat 16 {
                xx++
            }
            cx16.vpoke(1, $fa00+6*2, 0)
            cx16.vpoke(1, $fa01+6*2, 255)
            repeat 16 {
                xx++
            }
            cx16.vpoke(1, $fa00+6*2, 0)
            cx16.vpoke(1, $fa01+6*2, 0)
        }
        counter++
    }
}
