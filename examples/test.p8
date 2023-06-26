%import textio
%zeropage basicsafe

main {

    sub start()
    {
        ubyte variable=55
        when variable
        {
            33 -> txt.print("33")
        }

        if variable
        {
            txt.print("yes")
        }
        else
        {
            txt.print("no")
        }
    }
}

