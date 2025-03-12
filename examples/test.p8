%import psg
%zeropage basicsafe
%option no_sysinit

main {

    sub start()  {
        psg.init()

        psg.voice(5, psg.LEFT, 0, psg.TRIANGLE, 0)
        psg.freq(5, 1600)
        psg.envelope(5, 63, 10, 50, 2)

        psg.voice(6, psg.RIGHT, 0, psg.SAWTOOTH, 0)
        psg.freq(6, 1200)
        psg.envelope(6, 63, 2, 50, 10)

        repeat 140 {
            sys.waitvsync()
            psg.envelopes_irq()
        }

        psg.voice(5, psg.DISABLED, 0, 0, 0)
        psg.voice(6, psg.DISABLED, 0, 0, 0)
        psg.silent()
        repeat {
            sys.waitvsync()
            psg.envelopes_irq()
        }
    }
}
