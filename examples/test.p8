%import textio
%zeropage basicsafe

main
{
    sub start()
    {
        ; uword zc = $ea31
        word zc = -5583
        cx16.r1L = (zc>>10) as ubyte
        txt.print_ub(cx16.r1L)      ; 250
    }
}
