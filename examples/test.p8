%import textio
%zeropage basicsafe
%option no_sysinit

main {
    uword timercounter

    sub start() {
        cx16.enable_irq_handlers(true)
        cx16.set_timer1_irq_handler(&timerhandler)
        cx16.set_timer1(1000*8, true)       ; timer irq every 1000 milliseconds (assuming 8mhz clock rate)

        txt.cls()
        while timercounter <= 10000 {
            sys.waitvsync()
            txt.home()
            txt.print("via timer counter: ")
            txt.print_uw(timercounter)
        }

        cx16.disable_irq_handlers()
    }

    sub timerhandler() -> bool {
        timercounter++
        return false
    }
}
