%import c64utils

main {

    const uword COUNT = 16384
    const uword SQRT_COUNT = 128
    const uword Sieve = $4000

    sub sieve_round() {
        uword S
        ubyte I = 2
        memset(Sieve, COUNT, 0)
        while I < SQRT_COUNT {
            if @(Sieve + I) == 0 {
                S = Sieve + (I << 1)
                while S < Sieve + COUNT {
                    @(S) = 1
                    S += I
                }
            }
            I ++
        }
    }

    sub start() {
        benchcommon.begin()

        sieve_round()
        sieve_round()
        sieve_round()
        sieve_round()
        sieve_round()

        sieve_round()
        sieve_round()
        sieve_round()
        sieve_round()
        sieve_round()

        benchcommon.end()
    }
}



benchcommon {
    ubyte last_time0 = 0
    ubyte last_time1 = 0
    ubyte last_time2 = 0
    ubyte time_start0 = 0
    ubyte time_start1 = 0
    ubyte time_start2 = 0


    asmsub read_time () clobbers(A,X,Y) {
        %asm {{
            jsr $FFDE
            sta last_time0
            stx last_time1
            sty last_time2
            rts
        }}
    }

    sub begin() {
        benchcommon.read_time()
        benchcommon.time_start0 = benchcommon.last_time0
        benchcommon.time_start1 = benchcommon.last_time1
        benchcommon.time_start2 = benchcommon.last_time2
    }

    sub end() {
        benchcommon.read_time()

        c64scr.print_ubhex(benchcommon.time_start2, false)
        c64scr.print_ubhex(benchcommon.time_start1, false)
        c64scr.print_ubhex(benchcommon.time_start0, false)
        c64.CHROUT('\n')

        c64scr.print_ubhex(benchcommon.last_time2, false)
        c64scr.print_ubhex(benchcommon.last_time1, false)
        c64scr.print_ubhex(benchcommon.last_time0, false)
        c64.CHROUT('\n')

        void c64scr.input_chars($c000)
    }
}

