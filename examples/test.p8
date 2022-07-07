%import textio
%zeropage basicsafe

main  {

    bool boolvalue1 = true
    bool boolvalue2 = false
    ubyte @shared ubvalue1 = true
    ubyte @shared ubvalue2 = false

    sub thing(bool b1, bool b2) -> bool {
        return b1 and b2
    }

    sub start() {

        ubvalue1 = boolvalue1 & 1
        ubvalue2 = 1 & boolvalue1
        ubvalue1 = boolvalue1 & 0
        ubvalue2 = boolvalue1 & 8
        boolvalue1 = boolvalue2 & 1 ==0
        ; boolvar & 1 -> boolvar,   (boolvar & 1 == 0) -> not boolvar
     }
}
