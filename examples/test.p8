%import textio
%zeropage basicsafe

main
{
    ; 00f9
    sub start()
    {
        word bb

        when bb {
            0,1,22 -> bb+=10
            33,44,55 -> bb+=20
            42345 -> bb+=99
            else -> bb+=30
        }
    }
}
