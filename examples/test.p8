%import textio
%zeropage basicsafe

; $3d5

main {
    ubyte counter
    uword wcounter
    ubyte end
    uword wend

    sub start() {
        end=10
        for cx16.r2L in 0 to end-1 {
            txt.print_ub(cx16.r2L)
            txt.spc()
        }
        txt.nl()

        cx16.r2L=0
    labeltje:
            txt.print_ub(cx16.r2L)
            txt.spc()
            cx16.r2L++
            if cx16.r2L!=end
                goto labeltje
        txt.nl()

        cx16.r0=0
        forloops()
        txt.print_uw(cx16.r0)
        txt.nl()

        cx16.r0=0
        untilloops()
        txt.print_uw(cx16.r0)
        txt.nl()
    }

    sub forloops() {
        end=10
        for counter in 0 to end {
            cx16.r0++
        }
        for counter in 0 to end-1 {
            cx16.r0++
        }
        end=0
        for counter in 0 to end {
            cx16.r0++
        }
        for counter in 0 to end-1 {
            cx16.r0++
        }
        end=255
        for counter in 0 to end {
            cx16.r0++
        }
        for counter in 0 to end-1 {
            cx16.r0++
        }

        wend=1000
        for wcounter in 0 to wend {
            cx16.r0++
        }
        for wcounter in 0 to wend-1 {
            cx16.r0++
        }
    }

    sub untilloops() {

        end=10
        counter = 0
        repeat {
            cx16.r0++
            if counter==end
                break
            counter++
        }
        counter = 0
        do {
            cx16.r0++
            counter++
        } until counter==end

        end=0
        counter = 0
        repeat {
            cx16.r0++
            if counter==end
                break
            counter++
        }
        counter = 0
        do {
            cx16.r0++
            counter++
        } until counter==end

        counter = 0
        end=255
        repeat {
            cx16.r0++
            if counter==end
                break
            counter++
        }
        counter = 0
        do {
            cx16.r0++
            counter++
        } until counter==end

        wcounter = 0
        wend=1000
        repeat {
            cx16.r0++
            if wcounter==wend
                break
            wcounter++
        }
        wcounter = 0
        do {
            cx16.r0++
            wcounter++
        } until wcounter==wend
    }
}
