%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ubyte[] uba = [1,2,3]
        byte[] bba = [1,2,3]
        uword[] uwa = [1111,2222,3333]
        word[] wwa = [1111,2222,3333]

        ubyte ub
        byte bb
        uword uw
        word ww

        for ub in uba {
            c64scr.print_ub(ub)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for bb in bba {
            c64scr.print_b(bb)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for uw in uwa {
            c64scr.print_uw(uw)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for ww in wwa {
            c64scr.print_w(ww)
            c64scr.print(",")
        }
        c64scr.print("\n")

        for ub in [1,2,3] {
            c64scr.print_ub(ub)
            c64scr.print(",")
        }
;        c64scr.print("\n")
;        for bb in [1,2,3] {     ; TODO fix array literal conversion error
;            c64scr.print_b(bb)
;            c64scr.print(",")
;        }
        c64scr.print("\n")
        for uw in [1111,2222,3333] {
            c64scr.print_uw(uw)
            c64scr.print(",")
        }
;        c64scr.print("\n")
;        for ww in [1111,2222,3333] {        ; TODO fix array literal conversion error
;            c64scr.print_w(ww)
;            c64scr.print(",")
;        }
        c64scr.print("\n")

        ubyte[] ubb1 = [ 1 ]
        ubyte[] ubb2 = [ 1, 2]
        ubyte[] ubb3 = [ 1,2,3 ]
        ubyte[] ubb4 = [ 1,2,3,4 ]
        uword[] uww1 = [111]
        uword[] uww2 = [111,222]
        uword[] uww3 = [111,222,333]
        uword[] uww4 = [111,222,333,444]

        reverse(ubb1)
        reverse(ubb2)
        reverse(ubb3)
        reverse(ubb4)
        reverse(uww1)
        reverse(uww2)
        reverse(uww3)
        reverse(uww4)
        for ub in ubb1 {
            c64scr.print_ub(ub)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for ub in ubb2 {
            c64scr.print_ub(ub)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for ub in ubb3 {
            c64scr.print_ub(ub)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for ub in ubb4 {
            c64scr.print_ub(ub)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for uw in uww1 {
            c64scr.print_uw(uw)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for uw in uww2 {
            c64scr.print_uw(uw)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for uw in uww3 {
            c64scr.print_uw(uw)
            c64scr.print(",")
        }
        c64scr.print("\n")
        for uw in uww4 {
            c64scr.print_uw(uw)
            c64scr.print(",")
        }
        c64scr.print("\n")

        float[]  fa = [1.1, 2.2, 3.3, 4.4, 5.5]
        reverse(fa)
        for ub in 0 to len(fa)-1 {
            c64flt.print_f(fa[ub])
            c64scr.print(",")
        }
        c64scr.print("\n")

    }
}
