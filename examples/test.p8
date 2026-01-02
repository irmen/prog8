%import math
%import textio
%zeropage basicsafe

main {

    sub start()  {

        cx16.r0 = 12345
        cx16.r1 = 22222
        txt.print_uw(math.diffw(cx16.r0, cx16.r1))
        txt.nl()

        cx16.r2L = 99
        cx16.r3L = 200
        txt.print_ub(math.diff(cx16.r2L, cx16.r3L))
        txt.nl()

;        const long longconst = $12345678
;        long @shared longvar = $abcdef99
;
;        txt.print_uwhex(msw(longconst), true)
;        txt.spc()
;        txt.print_ubhex(lsb(msw(longconst)), true)
;        txt.nl()
;
;        txt.print_uwhex(msw(longvar), true)
;        txt.spc()
;        txt.print_ubhex(lsb(msw(longvar)), true)
;        txt.spc()
;        txt.print_ubhex(@(&longvar+2), true)
;        txt.nl()
    }
}
