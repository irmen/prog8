%import textio
%zeropage basicsafe

main
{
    sub start()
    {
        word zc = -5583
        txt.print_w(zc)
        txt.spc()
        txt.print_w(zc>>10)  ; -6
        txt.nl()

        txt.print_w(zc)
        txt.spc()
        txt.print_w(zc>>4)   ; -349
        txt.nl()

        txt.print_w(zc)
        txt.spc()
        txt.print_w(zc>>10)  ; -6
        txt.nl()

        cx16.r1L = (zc>>10) as ubyte
        txt.print_ub(cx16.r1L)      ; 250
        txt.nl()
        cx16.r1L = (zc>>4) as ubyte
        txt.print_ub(cx16.r1L)      ; 163
        txt.nl()
    }
}
