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
; Note on units. Three different clocks are in play here and none of them is a
; stopwatch, so every elapsed time and rate below is *measured* with
; timer.device instead of being multiplied out from a sleep count:
;   - dos.library Delay() - and therefore sys.wait() - takes a number of OS
;     ticks. A tick is 1/50 s on PAL and 1/60 s on NTSC, and Delay() is
;     quantised to tick boundaries, so sys.wait(1) costs noticeably more than
;     one tick (measured at ~40 ms, i.e. two ticks, on a PAL machine). Treat it
;     as "yield the CPU and come back a bit later", never as a delay you can do
;     arithmetic on: counting sleeps and multiplying by 20 overestimates how long
;     the program runs by that factor.
;   - lowlevel.library StartTimerInt() takes its interval in MICROSECONDS; the
;     autodoc gives 90000 as the maximum.
;   - timer.device TR_GETSYSTIME is the only real clock used below.

%import lowlevel
%import textio
%import timer

main {
    const uword TOTAL_MS = 3000            ; run for about three seconds
    const uword REPORT_EVERY_MS = 500      ; report every half second

    const uword SLICE_TICKS = 1            ; only ever used as "sleep one tick"

    ; Microseconds between interrupts. The lowlevel.library autodoc states this
    ; argument is in microseconds, with 90000 as the maximum. 20000 (20 ms) is
    ; a gentle rate that will not distort the measurement by swamping the CPU
    ; with interrupts; raise it if you want to watch a faster one.
    const long TIMER_INTERVAL_US = 20000

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
    ; allocation) or waits on VBlank does not belong in here.
    sub timer_irq() {
        ticks++
    }

    ; milliseconds since boot, read from timer.device
    sub systime_ms() -> long {
        long secs, micros = timer.getsystime()
        return secs * 1000 + micros / 1000
    }

    sub report(long elapsed_ms, uword count) {
        txt.print("t=")
        txt.print_l(elapsed_ms)
        txt.print("ms  interrupts=")
        txt.print_uw(count)
        txt.nl()
    }

    sub start() {
        if not timer.opendevice() {
            txt.print("can't open timer.device\n")
            return
        }
        if not lowlevel.openlib() {
            txt.print("can't open lowlevel.library\n")
            timer.closedevice()
            sys.exit(101)
        }
        txt.print("lib open\n")

        pointer handle = lowlevel.AddTimerInt(&timer_irq, 0)
        if handle == 0 {
            ; A null handle means the timer was already claimed. On OCS/ECS the
            ; CIA timer B is shared with input.device, so not a theoretical case.
            txt.print("AddTimerInt returned NULL: timer already in use\n")
            lowlevel.closelib()
            timer.closedevice()
            sys.exit(102)
        }
        lowlevel.StartTimerInt(handle, TIMER_INTERVAL_US, true)
        txt.print("timer started, running for ~3 seconds\n")

        ; the loop is paced by the measured clock, not by a sleep count, so the
        ; run really does last about TOTAL_MS on any machine
        long started = systime_ms()
        long elapsed = 0
        long next_report = REPORT_EVERY_MS
        while elapsed < TOTAL_MS {
            sys.wait(SLICE_TICKS)
            elapsed = systime_ms() - started
            if elapsed >= next_report {
                report(elapsed, ticks)
                next_report = elapsed + REPORT_EVERY_MS
            }
        }

        ; snapshot the counter while the timer is still running
        uword fired = ticks
        long us_each = 0
        if fired != 0
            us_each = elapsed * 1000 / fired

        txt.print("done: ")
        txt.print_l(elapsed)
        txt.print(" ms, ")
        txt.print_uw(fired)
        txt.print(" interrupts (")
        txt.print_l(us_each)
        txt.print(" us each, asked for ")
        txt.print_l(TIMER_INTERVAL_US)
        txt.print(")\n")

        if fired == 0 {
            txt.print("VERDICT: no interrupts at all - the timer is not firing\n")
        } else if us_each > TIMER_INTERVAL_US * 3 / 2 {
            txt.print("VERDICT: firing, but well below the requested rate\n")
        } else {
            txt.print("VERDICT: OK - handler ran, returning with rts works\n")
        }

        lowlevel.RemTimerInt(handle)
        lowlevel.closelib()
        timer.closedevice()
        txt.print("clean exit\n")
        sys.exit(0)
    }
}
