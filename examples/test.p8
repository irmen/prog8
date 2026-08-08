%import textio
%zeropage basicsafe

main {
    sub start() {
        uword @shared ptr1
        long @shared ptr2
        pointer @shared ptr3
        ^^bool @shared ptr4
        ^^word @shared ptr5

        ubyte x = ptr1[10]      ; should fail on 32 bits target
        ubyte y = ptr2[10]
        ubyte z = ptr3[10]
        bool q = ptr4[10]
        word r = ptr5[10]

        ptr1[10]++      ; should fail on 32 bits target
        ptr2[10]++
        ptr3[10]++
        ptr4[10] = true
        ptr5[10] = -32768
    }
}
