%import textio
%import sorting
%import strings
%import emudbg
%zeropage basicsafe

main {
    sub start() {
        ubyte[19] keys
        str[19] @nosplit values = [ "the", "narrator", "begins", "by", "describing", "the", "hole", "in", "the", "ground", "beneath", "a", "hill", "in", "which", "a", "particular", "hobbit", "lives"]

        uword[19] @nosplit wkeys
        str[19] @nosplit wvalues = [ "the", "narrator", "begins", "by", "describing", "the", "hole", "in", "the", "ground", "beneath", "a", "hill", "in", "which", "a", "particular", "hobbit", "lives"]

        for cx16.r0L in 0 to len(keys)-1 {
            keys[cx16.r0L] = strings.length(values[cx16.r0L])
            wkeys[cx16.r0L] = strings.length(wvalues[cx16.r0L])
        }

        perf_reset()
        repeat 100 {
            sorting.gnomesort_by_ub(keys, values, len(keys))
        }
        perf_print()
        dump()
        txt.nl()
        txt.nl()

        txt.nl()
        perf_reset()
        repeat 100 {
            sorting.gnomesort_by_uw(wkeys, wvalues, len(wkeys))
        }
        perf_print()
        dumpw()

        sub dump() {
            for cx16.r0L in 0 to len(keys)-1 {
                txt.print_ub(keys[cx16.r0L])
                txt.spc()
                txt.spc()
                txt.print(values[cx16.r0L])
                txt.nl()
            }
        }

        sub dumpw() {
            for cx16.r0L in 0 to len(wkeys)-1 {
                txt.print_uw(wkeys[cx16.r0L])
                txt.spc()
                txt.spc()
                txt.print(wvalues[cx16.r0L])
                txt.nl()
            }
        }
    }


    sub perf_reset() {
        emudbg.reset_cpu_cycles()
    }

    sub perf_print() {
        cx16.r4, cx16.r5 = emudbg.cpu_cycles()
        txt.print_uwhex(cx16.r5, true)
        txt.print_uwhex(cx16.r4, false)
        txt.nl()
    }
}
