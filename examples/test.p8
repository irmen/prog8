%zeropage basicsafe

main {

    sub start() {

        cx16.r0L = 2020 - 1900
        cx16.r0H = 8
        cx16.r1L = 26
        cx16.r1H = 19
        cx16.r2L = 16
        cx16.r2H = 0
        cx16.r3L = 0
        cx16.clock_set_date_time()
        c64.CHROUT(14)      ; lowercase charset

        repeat {
            c64.CHROUT(19)      ; HOME
            screen.print(" yyyy-mm-dd HH:MM:SS.jj\n\n")
            cx16.clock_get_date_time()
            c64.CHROUT(' ')
            print_date()
            c64.CHROUT(' ')
            print_time()
	    }
    }

    sub print_date() {
        screen.print_uw(1900 + cx16.r0L)
        c64.CHROUT('-')
        if cx16.r0H < 10
            c64.CHROUT('0')
        screen.print_ub(cx16.r0H)
        c64.CHROUT('-')
        if cx16.r1L < 10
            c64.CHROUT('0')
        screen.print_ub(cx16.r1L)
    }

    sub print_time() {
        if cx16.r1H < 10
           c64.CHROUT('0')
        screen.print_ub(cx16.r1H)
        c64.CHROUT(':')
        if cx16.r2L < 10
            c64.CHROUT('0')
        screen.print_ub(cx16.r2L)
        c64.CHROUT(':')
        if cx16.r2H < 10
            c64.CHROUT('0')
        screen.print_ub(cx16.r2H)
        c64.CHROUT('.')
        if cx16.r3L < 10
            c64.CHROUT('0')
        screen.print_ub(cx16.r3L)
    }
}

