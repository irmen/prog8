%import textio
%zeropage basicsafe

main {
    sub start() {
        uword[4] array = [1,2,3,4]
        ubyte index = 2

        cx16.r0 = 99
        array[index] += 12345
        array[index] += cx16.r0
        array[index] += index
        txt.print_uw(array[index])      ; prints 12449

        ; code size = $0249

    }
}
