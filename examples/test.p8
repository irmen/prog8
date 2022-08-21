main {
    sub start() {

        uword @shared slab1 = memory("slab 1", 2000, 0)
        uword @shared slab2 = memory("slab 1", 2000, 0)
        uword @shared slab3 = memory("other # slab", 2000, 64)

        uword @shared zz = slab1+slab2+slab3

        uword @shared qq = zz
        uword @shared qq2 = &zz

        qq=4242             ; TODO should generate symbol not allocated address
        c64.EXTCOL = 42     ; TODO wrong VMASM code generated... should generate mapped memory address
    }
}
