%zeropage basicsafe

main {

    sub start() {
        printf([1111,2,3,4444])
        printf([1111,2,3,"bar"])
        printf([1,2,3])
        printf([1111,2,3,-4444])

    }

    sub printf(uword argspointer) {
        cx16.r0++
    }
}
