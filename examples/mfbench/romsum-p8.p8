%import c64utils

main {

    const uword rom = $e000

    sub sumrom() -> uword {
        uword p = rom
        uword s = 0
        ubyte i
        repeat $20 {
            for i in 0 to $ff {
                s += @(p+i)
            }
            p += $100
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

