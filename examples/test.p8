main {

    uword global1 = 1234

    sub start() {

        ; TODO should generate address
        uword @shared slab1 = memory("slab 1", 2000, 0)
        uword @shared slab2 = memory("slab 1", 2000, 0)
        uword @shared slab3 = memory("other # slab", 2000, 64)
        &uword mapped = $c000

        uword @shared zz = slab1+slab2+slab3

        uword @shared qq = zz
        uword @shared qq2 = &zz

        qq=4242             ; TODO should generate symbol not allocated address
        mapped = 42     ; TODO wrong VMASM code generated... should generate mapped memory address

        qq=global1
        qq=other.global2
        ; nested()

        ; TODO flatten nested subroutines in codegen
        sub nested() {
            qq++
        }
    }
}

other {

    uword global2 = 9999

}
