%import textio
%zeropage basicsafe

main
{
    sub start()
    {
        uword zc = 54321
        ubyte zb = 123
        ubyte shift = 2

;        txt.print_uw(zc<<shift)
;        txt.nl()
;        txt.print_uw(zc>>shift)
;        txt.nl()
;        txt.print_ub(zb<<shift)
;        txt.nl()
;        txt.print_ub(zb>>shift)
;        txt.nl()
;
;        word szc = -12345
;        byte szb = -123
;        txt.print_w(szc<<shift)
;        txt.nl()
;        txt.print_w(szc>>shift)
;        txt.nl()
;        txt.print_b(szb<<shift)
;        txt.nl()
;        txt.print_b(szb>>shift)
;        txt.nl()
;
        txt.print_uw(~zc as ubyte)
        txt.spc()
        txt.print_uw(~zb as uword)
        txt.nl()


;        cx16.r1L = (zc<<shift) as ubyte
;        txt.print_ub(cx16.r1L)
    }
}
