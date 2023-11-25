%import textio
%zeropage basicsafe

main {
    ubyte counter
    uword wcounter
    ubyte end=10
    uword wend=10

    sub start() {
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
        end=0
        for counter in 0 to end {
            cx16.r0++
        }
        end=255
        for counter in 0 to end {
            cx16.r0++
        }

        wend=1000
        for wcounter in 0 to wend {
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

        end=0
        counter = 0
        repeat {
            cx16.r0++
            if counter==end
                break
            counter++
        }

        counter = 0
        end=255
        repeat {
            cx16.r0++
            if counter==end
                break
            counter++
        }

        wcounter = 0
        wend=1000
        repeat {
            cx16.r0++
            if wcounter==wend
                break
            wcounter++
        }
    }
}
