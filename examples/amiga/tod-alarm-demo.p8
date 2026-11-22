; tod-alarm-demo.p8 - CIA-A TOD alarm interrupt demonstration.
;
; The TOD alarm fires about twice a second. The handler acknowledges the
; interrupt, paints COLOR00 with a random colour. Exits on left mouse button.
; Bare-metal: takes over the system with grab_system().

%import custom
%import math
%import textio

main {
    uword @shared alarmcount = 0

    sub tod_handler() {
        ; read_ciaa_icr also clears the latched level-2 INTREQ bit.
        ubyte icr = custom.read_ciaa_icr()
        ; Check TOD alarm (bit 2).
        if (icr & custom.CIAICRF_ALRM) != 0 {
            alarmcount++
            custom.COLOR00 = math.rndw() & $0fff
            ; The alarm flag is set once per match, so re-arm it 25 ticks ahead.
            custom.tod_alarm_set(custom.CIA::A, custom.tod_read(custom.CIA::A) + 25)
        }
    }

    sub start() {
        txt.print("CIA TOD alarm demo. The background colour changes on every alarm.\n")
        txt.print("Click the left mouse button to exit.\n")
        sys.wait(120)

        custom.grab_system(true)

        ; Alarm at 25 ticks (half a second at 50 Hz), then install the handler
        ; and enable the interrupt source.
        custom.tod_write(custom.CIA::A, 0)
        custom.tod_alarm_set(custom.CIA::A, 25)
        custom.set_ciaa_tod_handler(&tod_handler)

        while not custom.left_button() {
            ; wait for the left mouse click
        }

        ; Clean shutdown.
        custom.disable_ciaa()
        custom.tod_alarm_enable(custom.CIA::A, false)
        custom.restore_system()
    }
}
