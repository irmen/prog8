; CommanderX16 text datetime example!

%target cx16
%import textio
%zeropage basicsafe

main {

    sub start() {

        cx16.clock_set_date_time(mkword(8, 2020 - 1900), mkword(19, 27), mkword(0, 16), 0)
        txt.lowercase()

        repeat {
            txt.chrout(19)      ; HOME
            txt.print("\n yyyy-mm-dd HH:MM:SS.jj\n\n")
            void cx16.clock_get_date_time()
            txt.chrout(' ')
            print_date()
            txt.chrout(' ')
            print_time()

            txt.chrout('\n')
            txt.chrout('\n')
            uword jiffies = c64.RDTIM16()
            txt.print_uw(jiffies)
	}
    }

    sub print_date() {
        txt.print_uw(1900 + lsb(cx16.r0))
        txt.chrout('-')
        if msb(cx16.r0) < 10
            txt.chrout('0')
        txt.print_ub(msb(cx16.r0))
        txt.chrout('-')
        if lsb(cx16.r1) < 10
            txt.chrout('0')
        txt.print_ub(lsb(cx16.r1))
    }

    sub print_time() {
        if msb(cx16.r1) < 10
           txt.chrout('0')
        txt.print_ub(msb(cx16.r1))
        txt.chrout(':')
        if lsb(cx16.r2) < 10
            txt.chrout('0')
        txt.print_ub(lsb(cx16.r2))
        txt.chrout(':')
        if msb(cx16.r2) < 10
            txt.chrout('0')
        txt.print_ub(msb(cx16.r2))
        txt.chrout('.')
        if lsb(cx16.r3) < 10
            txt.chrout('0')
        txt.print_ub(lsb(cx16.r3))
    }
}

