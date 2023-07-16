%import textio
%zeropage basicsafe

main
{
    sub start()
    {
        uword uw = 54321
        ubyte ub = 123
        word sw = -12345
        byte sb = -123

        txt.print_uw(~ub as uword)  ;132
        txt.nl()
        txt.print_ub(~uw as ubyte)  ;206
        txt.nl()
        txt.print_uw(~sb as uword)  ;122
        txt.nl()
        txt.print_ub(~sw as ubyte)  ;56
        txt.nl()
        txt.print_w(-sb as word)    ;123
        txt.nl()
        txt.print_b(-sw as byte)    ;57
        txt.nl()
    }
}
