%import textio
%import psg2
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        psg2.init()
        cx16.enable_irq_handlers(false)
        cx16.set_vsync_irq_handler(&psg2.update)

        psg2.voice(1, psg2.BOTH, 60, psg2.SQUARE, 1)
        psg2.frequency(1, 240)
        psg2.envelope(1, 5, 60, 15)
        sys.wait(200)

        psg2.volume(1, 63)
        ^^psg2.Voice v1 = psg2.getvoice(1)
        repeat 7 {
            repeat 40 {
                v1.pulsewidth ++
                v1.frequency ++
                sys.waitvsync()
            }
            repeat 40 {
                v1.pulsewidth --
                v1.frequency ++
                sys.waitvsync()
            }
        }

        psg2.off()
        cx16.disable_irq_handlers()
    }
}
