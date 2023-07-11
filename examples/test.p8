%import textio
%zeropage basicsafe

main
{
    ; 00f9
    sub start()
    {
        bool rasterIrqAfterSubs=false
        when rasterIrqAfterSubs {
          false -> txt.print("false\n")
          true -> txt.print("true\n")
        }
        txt.print("done")
    }
}
