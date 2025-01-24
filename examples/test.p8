;%import emudbg
%import textio
%option no_sysinit
%zeropage basicsafe
%import math


main {
    sub start() {
        uword @shared w1, w2
        ubyte @shared b

        w1 = w2 + b*$0002
        w1 = w2 + b + b
;        w2 = (w1 + b as uword) + (b as uword)


;        cx16.r0 = cx16.r1 + cx16.r0L*2
;        cx16.r0 = cx16.r1 + cx16.r0L*$0002
;        cx16.r0 = cx16.r1 + cx16.r0L + cx16.r0L
;        cx16.r0 = cx16.r1 - cx16.r0L*2
;        cx16.r0 = cx16.r1 - cx16.r0L*$0002
;        cx16.r0 = cx16.r1 - cx16.r0L - cx16.r0L
    }
}


/*
mainxxx {

    uword[50] @nosplit warray1
    uword[50] @nosplit warray2

    sub fill_arrays() {
        math.rndseed(999,1234)
        for cx16.r0L in 0 to len(warray1)-1 {
            warray1[cx16.r0L] = math.rndw()
            warray2[cx16.r0L] = cx16.r0L * (100 as uword)
        }
        warray2[40] = 9900
        warray2[44] = 9910
        warray2[48] = 9920
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

    sub start() {
        sys.set_irqd()
        fill_arrays()

        txt.print("\ngnomesort (words):\n")
        perf_reset()
        gnomesort_uw(warray1, len(warray1))
        perf_print()
        for cx16.r0L in 0 to len(warray1)-1 {
            txt.print_uw(warray1[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

        txt.print("\ngnomesort (words) almost sorted:\n")
        perf_reset()
        gnomesort_uw(warray2, len(warray2))
        perf_print()
        for cx16.r0L in 0 to len(warray2)-1 {
            txt.print_uw(warray2[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()
        txt.nl()

        fill_arrays()

        txt.print("\ngnomesort_opt (words):\n")
        perf_reset()
        gnomesort_uw_opt(warray1, len(warray1))
        perf_print()
        for cx16.r0L in 0 to len(warray1)-1 {
            txt.print_uw(warray1[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()

        txt.print("\ngnomesort_opt (words) almost sorted:\n")
        perf_reset()
        gnomesort_uw_opt(warray2, len(warray2))
        perf_print()
        for cx16.r0L in 0 to len(warray2)-1 {
            txt.print_uw(warray2[cx16.r0L])
            txt.chrout(',')
        }
        txt.nl()
        sys.clear_irqd()
        repeat {
        }
    }


    sub gnomesort_uw(uword values, ubyte num_elements) {
        ; TODO optimize this more, rewrite in asm?
        ubyte @zp pos = 1
        while pos != num_elements {
            uword @requirezp ptr = values+(pos*$0002)
            cx16.r0 = peekw(ptr-2)
            cx16.r1 = peekw(ptr)
            if cx16.r0<=cx16.r1
                pos++
            else {
                ; swap elements
                pokew(ptr-2, cx16.r1)
                pokew(ptr, cx16.r0)
                pos--
                if_z
                    pos++
            }
        }
    }

    sub gnomesort_uw_opt(uword values, ubyte num_elements) {
        ; TODO optimize this more, rewrite in asm?
        ubyte @zp pos = 1
        uword @requirezp ptr = values+2
        while pos != num_elements {
            cx16.r0 = peekw(ptr-2)
            cx16.r1 = peekw(ptr)
            if cx16.r0<=cx16.r1 {
                pos++
                ptr+=2
            }
            else {
                ; swap elements
                pokew(ptr-2, cx16.r1)
                pokew(ptr, cx16.r0)
                if pos>1 {
                    pos--
                    ptr-=2
                }
            }
        }
    }
}
*/
