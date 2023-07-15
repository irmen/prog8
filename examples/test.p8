%import textio
%zeropage basicsafe

main
{
    sub start()
    {
        ubyte a=1
        ubyte b=2
        ubyte c=3
        ubyte d=4

        ubyte xx = (a*b)+(c*d)
        xx++

    }
}
