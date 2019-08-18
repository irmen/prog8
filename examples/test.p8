%import c64lib
%import c64utils
%zeropage basicsafe

main {

    sub start() {

        ubyte i
        for i in 0 to 10 {
            A=i
        }

        for i in 0 to 10 {
            Y=i
        }

;        ubyte[] uba = [10,0,2,8,5,4,3,9]
;        uword[] uwa = [1000,0,200,8000,50,40000,3,900]
;        byte[] ba = [-10,0,-2,8,5,4,-3,9,-99]
;        word[] wa = [-1000,0,-200,8000,50,31111,3,-900]
;
;        for ubyte ub in uba {
;            c64scr.print_ub(ub)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for uword uw in uwa {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for byte bb in ba {
;            c64scr.print_b(bb)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for word ww in wa {
;            c64scr.print_w(ww)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        sort(uba)
;        sort(uwa)
;        sort(ba)
;        sort(wa)
;
;        for ubyte ub2 in uba {
;            c64scr.print_ub(ub2)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for uword uw2 in uwa {
;            c64scr.print_uw(uw2)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for byte bb2 in ba {
;            c64scr.print_b(bb2)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for word ww2 in wa {
;            c64scr.print_w(ww2)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        reverse(uba)
;        reverse(uwa)
;        reverse(ba)
;        reverse(wa)
;
;        for ubyte ub3 in uba {
;            c64scr.print_ub(ub3)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for uword uw3 in uwa {
;            c64scr.print_uw(uw3)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for byte bb3 in ba {
;            c64scr.print_b(bb3)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for word ww3 in wa {
;            c64scr.print_w(ww3)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')


    }
}
