%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        const uword mem1 = memory("mem1", 10, 0)
        const uword mem2 = memory("mem2", 10, 0)
        uword[2] arr = [mem1, mem2]
        @(arr[0]) = 111
        @(arr[1]) = 222
    }
}
