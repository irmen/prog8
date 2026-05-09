%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ^^uword ptr1 = 4444         ; should NOT be made const because of data type size >1
        const ^^uword ptr1b = 4444  ; should give ERROR about not able to make const because of data type size >1
        ^^ubyte ptr2 = 5555         ; should be made CONST because data type sizse == 1

        uword mem1 = memory("mem1", 10, 0)      ; should be made CONST because data type sizse == 1
        ^^uword mem1b  = memory("mem2", 10, 0)   ; should NOT be made const because of data type size >1
        const ^^uword mem2  = memory("mem2", 10, 0)   ; should give ERROR about not able to make const because of data type size >1
        ^^ubyte mem3  = memory("mem3", 10, 0)   ; should be made CONST because data type sizse == 1

        cx16.r0 = ptr1
        cx16.r1 = ptr1b
        cx16.r2 = ptr2
        cx16.r3 = mem1
        cx16.r4 = mem1b
        cx16.r5 = mem2
        cx16.r6 = mem3
    }
}
