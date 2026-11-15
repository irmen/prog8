; Amiga hardware timers: the CIA TOD counters and the CIA interval timers.
;
; Every 8520 has a 24-bit TOD register, and on the Amiga a TOD is not a clock:
; it just counts the edges fed to its TOD pin. CIA-A counts 50/60 Hz events
; (mains tick, or VSync on later machines) and CIA-B counts horizontal sync at
; about 15.7 kHz, which makes it a line counter rather than a time of day.
; This program zeroes both counters, waits one second by counting CIA-A, and
; then reports what CIA-B counted over the same second: the lines per frame,
; measured instead of hardcoded. The TOD alarm serves as a one-shot at the end
; of the counter part.
;
; Each CIA also has two 16-bit interval timers counting the ~7 MHz ø2 clock.
; Those are used here to time a loop and to wait one millisecond. No interrupt
; is involved anywhere: Prog8 cannot install a CIA interrupt handler yet, so
; the flags are polled, which also shows how expensive a polling timer is.
;
; AmigaOS owns both counters while it runs. timer.device takes its system tick
; from CIA-A, and graphics.library uses CIA-B as its video beam follower, so
; writing either one disturbs the OS: that is why this program calls
; custom.grab_system() first. Under the OS, ask timer.device (or ReadEClock)
; for timing instead; reading a counter is harmless, but its value belongs to
; whoever owns it.

%import custom
%import textio

main {
    ubyte ticks_per_second
    ubyte alarm_ticks
    long frames = 0
    long lines = 0
    long alarm_count = 0
    bool no_edges = false
    bool alarm_fired = false

    const uword WORK_ITEMS = 200
    uword work_cycles = 0
    uword work_sum = 0
    bool timer_fired = false
    uword wait_lines = 0

    ; Guard for the polls below. An emulator that does not model the CIA TOD
    ; inputs never sees an edge, and without a bound these loops would spin
    ; forever. Reaching this many counter reads takes far longer than the 50
    ; or 60 ticks being waited for, but still gives up in a few seconds.
    const long SPIN_LIMIT = 500000

    sub start() {
        ; replace_view = false keeps display DMA running: the beam has to keep
        ; sweeping, or CIA-B has no horizontal sync edges left to count.
        custom.grab_system(false)
        ticks_per_second = if custom.isPAL then 50 else 60
        alarm_ticks = (ticks_per_second / 2) as ubyte

        measure_counters()
        measure_alarm()
        measure_loop()
        wait_one_ms()

        custom.restore_system()
        report()
    }

    ; Wait for one second of CIA-A events and read both counters. Writing a
    ; TOD stops the counter on the high-byte write and restarts it on the
    ; low-byte write, so starting both from zero means the two values below
    ; describe exactly one second.
    sub measure_counters() {
        custom.tod_write(custom.CIA::A, 0)
        custom.tod_write(custom.CIA::B, 0)
        no_edges = not wait_for_ticks(ticks_per_second)
        frames = custom.tod_read(custom.CIA::A)
        lines = custom.tod_read(custom.CIA::B)
    }

    sub wait_for_ticks(ubyte ticks) -> bool {
        long target = ticks as long
        long spins = 0
        while custom.tod_read(custom.CIA::A) < target {
            spins += 1
            if spins > SPIN_LIMIT
                return false
        }
        return true
    }

    ; The alarm as a one-shot timer: load it with half a second from now and
    ; poll for the flag. Matching the alarm does not stop or reload the
    ; counter, it only sets ICR bit 2, so the counter is read afterwards to
    ; see how long the wait really took. Note that reading the ICR
    ; acknowledges every CIA-A flag, not just the alarm one; that is also
    ; what releases the CIA interrupt line before the OS gets the machine
    ; back.
    sub measure_alarm() {
        custom.tod_write(custom.CIA::A, 0)
        custom.tod_alarm_set(custom.CIA::A, alarm_ticks as long)
        custom.tod_alarm_enable(custom.CIA::A, true)
        bool fired = false
        long spins = 0
        while not fired {
            fired = (custom.CIAA_ICR & custom.CIAICRF_ALRM)!=0
            spins += 1
            if spins > SPIN_LIMIT
                break
        }
        custom.tod_alarm_enable(custom.CIA::A, false)
        if fired {
            alarm_fired = true
            alarm_count = custom.tod_read(custom.CIA::A)
        }
    }

    ; Times a small fixed loop with CIA-B timer A: load the latch with $ffff,
    ; run the loop, then read what is left. The counter runs down from the
    ; ~7 MHz clock (7.09 MHz PAL, 7.16 MHz NTSC), so the difference is the
    ; number of clock cycles the loop cost. Continuous mode is enough here;
    ; one underflow of 65535 ticks covers about 9 ms, far more than the loop.
    sub measure_loop() {
        custom.timer_start(custom.CIA::B, custom.TIMER::A, $ffff, true)
        work_sum = sum_up_to(WORK_ITEMS)
        work_cycles = $ffff - custom.timer_value(custom.CIA::B, custom.TIMER::A)
        custom.timer_stop(custom.CIA::B, custom.TIMER::A)
    }

    sub sum_up_to(uword n) -> uword {
        uword total = 0
        while n > 0 {
            total += n
            n -= 1
        }
        return total
    }

    ; Waits one millisecond with CIA-B timer B in one-shot mode by polling its
    ; flag, then reads the beam position: one millisecond at 312 lines and a
    ; 20 ms frame is about 15 lines, so the line counter is a rough check on
    ; how long the wait actually took, including the polling overhead.
    sub wait_one_ms() {
        uword ms_ticks = if custom.isPAL then 7094 else 7159
        long before = custom.tod_read(custom.CIA::B)
        custom.timer_start(custom.CIA::B, custom.TIMER::B, ms_ticks, false)
        long spins = 0
        while not timer_fired {
            timer_fired = custom.timer_flag(custom.CIA::B, custom.TIMER::B)
            spins += 1
            if spins > SPIN_LIMIT
                break
        }
        custom.timer_stop(custom.CIA::B, custom.TIMER::B)
        wait_lines = (custom.tod_read(custom.CIA::B) - before) as uword
    }

    sub report() {
        txt.print("Prog8 Amiga hardware timers\n\n")
        if not no_edges {
            txt.print("CIA-A TOD, one second of 50/60 Hz events: ")
            txt.print_l(frames)
            txt.print("\nCIA-B TOD, horizontal sync in that same second: ")
            txt.print_l(lines)
            txt.print("\n\nLines per frame (CIA-B / CIA-A): ")
            txt.print_l(lines / frames)
            txt.print("\n")
        } else {
            txt.print("The CIA TOD inputs are not ticking here (emulator?), so\n")
            txt.print("both counters stayed at the value written to them.\n")
        }
        txt.print("\nAlarm set at ")
        txt.print_ub(alarm_ticks)
        txt.print(" ticks: ")
        if alarm_fired {
            txt.print("fired when the counter read ")
            txt.print_l(alarm_count)
        } else {
            txt.print("never fired (counter not ticking)")
        }
        txt.print("\n\nTimer A: sum of 1..")
        txt.print_uw(WORK_ITEMS)
        txt.print(" = ")
        txt.print_uw(work_sum)
        txt.print(" in ")
        txt.print_uw(work_cycles)
        txt.print(" clock cycles, that is ")
        txt.print_uw(work_cycles / WORK_ITEMS)
        txt.print(" per round trip\n")
        txt.print("\nTimer B: one millisecond waited by polling, the beam moved ")
        txt.print_uw(wait_lines)
        txt.print(" lines (about 15 expected)")
        if timer_fired {
            txt.print(", flag seen")
        } else {
            txt.print(", flag never set")
        }
        txt.print("\n")
    }
}
