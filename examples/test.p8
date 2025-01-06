%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        uword[10] @align64 @nosplit array1
        uword[10] @align64 @split array2

        array1[2]++
        array2[2]++
    }
}
