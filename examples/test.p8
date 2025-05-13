%import textio
%zeropage basicsafe
%option romable


main {
    sub start() {
        for cx16.r0L in "irmen" {
            txt.nl()
            txt.chrout(cx16.r0L)
            for cx16.r1L in "green" {
                txt.chrout(cx16.r1L)
            }
        }

        for cx16.r0L in "irmen" {
            txt.nl()
            txt.chrout(cx16.r0L)
            for cx16.r1L in "blue" {
                txt.chrout(cx16.r1L)
            }
        }

        for cx16.r0L in "irmen" {
            txt.nl()
            txt.chrout(cx16.r0L)
            for cx16.r1L in "red" {
                txt.chrout(cx16.r1L)
            }
        }

        for cx16.r0L in [11,22,33,44]
            cx16.r1L++
        for cx16.r0L in [11,22,33,44]
            cx16.r1L++
        for cx16.r0L in [11,22,33,44]
            cx16.r1L++

        bool z

        for z in [true, true, false, false]
            cx16.r1L++
        for z in [true, true, false, false]
            cx16.r1L++
        for z in [true, true, false, false]
            cx16.r1L++

        for cx16.r0 in [1111,2222,3333]
            cx16.r1L++
        for cx16.r0 in [1111,2222,3333]
            cx16.r1L++
        for cx16.r0 in [1111,2222,3333]
            cx16.r1L++

;        repeat 2 {
;            repeat 2 {
;                repeat 260 {
;                    repeat 260 {
;                        cx16.r0++
;                    }
;                }
;            }
;        }
;
;        txt.print_uw(cx16.r0)
;        txt.nl()
;        cx16.r0=0
;
;        repeat 2 {
;            repeat 2 {
;                repeat 260 {
;                    repeat 260 {
;                        cx16.r0++
;                    }
;                }
;            }
;        }
;
;        txt.print_uw(cx16.r0)
;        txt.nl()
    }
}
