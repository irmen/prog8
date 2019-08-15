%import c64lib
%import c64utils
%import c64flt
%zeropage dontuse

main {

    sub start() {

        uword start=1027
        uword stop=2020
        uword i
        ubyte ib

        c64scr.print("\n\n\n\n\n\n\n\n")
        memset($0400, 40*25, 30)

        ubyte ibstart = 1
        for ib in ibstart to 255-ibstart {
            @(ib+1024) = 44
        }

        for ib in 253 to 2 step -1 {
            @(ib+1024) = 3
        }

        ibstart = 3
        for ib in 255-ibstart to ibstart step -1 {
            @(ib+1024) = 45
        }


        for i in 1025 to 2022 {
            @(i) = 1
        }

        for i in 2021 to 1026 step -1 {
            @(i) = 92
        }

        for i in start to stop {
            @(i) = 0
        }

        for i in stop-1 to start+1 step -1 {
            @(i) = 91
        }


        ubyte xx=X
        c64scr.print_ub(xx)


;        for i in stop to start {
;            c64scr.print_uw(i)
;            c64.CHROUT(',')
;        }

    }
}
