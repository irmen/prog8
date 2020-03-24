%import c64lib
%import c64utils
%zeropage basicsafe


main {
    uword[2] array1 = 1        ; TODO fix compiler crash about init value type


    sub start() {
        uword addr = $c000
        &uword addr2 = $c100

        ; not sure if these are okay:
        addr2 =  0
        addr2 |= 128
        addr2 += 1

        @(addr) = 0
        @(addr) |= 128      ; TODO FIX result of memory-OR/XOR and probably AND as well
        @(addr) += 1      ; TODO fix result of memory += 1

    }
}


