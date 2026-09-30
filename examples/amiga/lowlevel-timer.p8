; lowlevel.library timer interrupt, end to end.
;
; Installs a periodic timer interrupt whose handler does the least it possibly
; can - bump one counter - then reports that counter while the program runs for
; about three seconds, so you can see the interrupts arriving. The main loop
; sleeps in dos.library's Delay (via sys.wait) rather than spinning, so the OS
; yields the CPU and services the interrupt properly.
;
; lowlevel.library only exists on Kickstart 2.0+, so on a 1.8 machine this
; prints a message and exits rather than crashing.
;
; Note on units: dos.library Delay() - and therefore sys.wait() - counts in
; ticks of 1/50 second (20ms), NOT microseconds, and cannot express a shorter
; wait than a single tick. The runtime below is therefore a fixed number of
; sleeps rather than a busy loop.

%import lowlevel
%import textio

main {
    const uword SLICE_TICKS = 1        ; 20ms per sleep
    const uword TOTAL_SLICES = 150     ; 150 * 20ms = ~3 seconds
    const uword REPORT_EVERY = 25      ; report every ~0.5 seconds

    ; UNCONFIRMED: the unit of StartTimerInt's timeInterval argument. The
    ; generated binding carries no unit, and the same library's Delay() counts
    ; 50Hz ticks, so this may well be ticks too. Kept small so the run stays
    ; quick under either reading; correct it once the lowlevel autodoc is to
    ; hand. The verdict printed at the end will tell you which it is.
    const long TIMER_INTERVAL = 1000

    ; A word counter on purpose: an aligned word read or write is atomic on the
    ; 68000, so the main loop can never see a torn value. A long counter could be
    ; read half-updated by the handler.
    uword @shared ticks

    ; The interrupt handler. lowlevel's AddTimerInt calls this with a jsr from
    ; its own interrupt handler, so it is an ordinary subroutine and returns with
    ; a normal rts - which means it can be plain Prog8, no inline asm needed.
    ; (It must NOT end in rte: that would pop a status register which was never
    ; pushed, leaving the stack 2 bytes short and crashing on the first
    ; interrupt.)
    ; Keep the body this short regardless - it runs in interrupt context. If you
    ; add real work here, remember that anything which can block (disk, DOS,
    ; allocation) or wait on VBlank does not belong in here.
    sub timer_irq() {
        ticks++
    }

    sub report(uword slices, uword count) {
        txt.print("t=")
        txt.print_uw(slices)
        txt.print(" (")
        txt.print_uw(slices * 20)
        txt.print("ms)  interrupts=")
        txt.print_uw(count)
        txt.nl()
    }

    sub start() {
        if not lowlevel.openlib() {
            txt.print("openlib FAILED: needs Kickstart 2.0+\n")
            sys.exit(101)
        }
        txt.print("lib open\n")

        pointer handle = lowlevel.AddTimerInt(&timer_irq, 0)
        if handle == 0 {
            ; A null handle means the timer was already claimed. On OCS/ECS the
            ; CIA timer B is shared with input.device, so not a theoretical case.
            txt.print("AddTimerInt returned NULL: timer already in use\n")
            lowlevel.closelib()
            sys.exit(102)
        }
        lowlevel.StartTimerInt(handle, TIMER_INTERVAL, true)
        txt.print("timer started, running for ~3 seconds\n")

        uword slices = 0
        uword reported = 0
        while slices < TOTAL_SLICES {
            sys.wait(SLICE_TICKS)
            slices++
            if slices - reported >= REPORT_EVERY {
                reported = slices
                report(slices, ticks)
            }
        }

        txt.print("done: ")
        txt.print_uw(slices)
        txt.print(" slices, ")
        txt.print_uw(ticks)
        txt.print(" interrupts\n")
        if ticks == 0 {
            txt.print("VERDICT: no interrupts at all - the timer is not firing\n")
        } else if ticks < slices {
            txt.print("VERDICT: firing, but slower than one per 20ms slice\n")
            txt.print("         check the timeInterval unit (see TIMER_INTERVAL)\n")
        } else {
            txt.print("VERDICT: OK - handler ran, returning with rts works\n")
        }

        lowlevel.RemTimerInt(handle)
        lowlevel.closelib()
        txt.print("clean exit\n")
        sys.exit(0)
    }
}
