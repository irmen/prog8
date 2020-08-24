%import c64utils

main {

    const uword rom = $e000

    sub sumrom() -> uword {
        uword p = rom
        uword s = 0
        ubyte i
        repeat $20 {
            repeat $100 {
                s += @(p)
                p++
            }
        }
        return s
    }

    sub start() {
        benchcommon.begin()
        ubyte i
        for i in 0 to 5 {
            c64scr.print_uw(sumrom())
            c64.CHROUT('\n')
        }
        benchcommon.end()
    }
}


benchcommon {
    uword last_time = 0
    uword time_start = 0


    asmsub read_time () clobbers(A,X,Y) {
        %asm {{
            jsr $FFDE
            sta last_time
            stx last_time+1
            rts
        }}
    }

    sub begin() {
        benchcommon.read_time()
        benchcommon.time_start = benchcommon.last_time
    }

    sub end() {
        benchcommon.read_time()

        c64scr.print_uwhex(benchcommon.last_time-benchcommon.time_start, false)
        c64.CHROUT('\n')

        void c64scr.input_chars($c000)
    }
}

