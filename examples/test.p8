%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start()  {
        cx16.r0L = 25

        when cx16.r0L {
            0 -> txt.print("zero")
            1 -> txt.print("one")
            21 to 29 step 2 -> txt.print("between 20 and 30 and odd")
            else -> txt.print("something else")
        }
    }
}
