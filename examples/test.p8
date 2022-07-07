%import textio
%zeropage basicsafe

main  {

    sub thing(bool b1, bool b2) -> bool {
        return (b1 and b2) or b1
    }

    sub start() {
        bool boolvalue1 = true
        bool boolvalue2 = false
        uword xx

        boolvalue1 = thing(true, false)
        boolvalue2 = thing(xx, xx)

        if boolvalue1 and boolvalue2
            boolvalue1=false
     }
}
