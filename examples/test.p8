%zpreserved 10,20
%zpreserved 30,40

main {

    uword global1 = 1234

    %asm {{
        nop
        nop
        return
    }}

    asmsub testasmsub(ubyte arg1 @A) clobbers(Y) -> uword @AX {
        %asm {{
            nop
            return
        }}
    }

    sub start() {
        sys.wait(1)

        %asm {{
            nop
            jump  a_label
        }}
a_label:

; TODO add proper memory SLAB support to IR+VM
;        uword @shared slab1 = memory("slab 1", 2000, 0)
;        uword @shared slab2 = memory("slab 1", 2000, 0)
;        uword @shared slab3 = memory("other # slab", 2000, 64)

        &uword mapped = $c000
        &ubyte[20] mappedarray = $c100

        uword @shared zz
        ; TODO  zz = slab1+slab2+slab3

        uword @shared @zp qq = zz
        uword @shared @zp qq2 = &zz

        str @shared namestring = "irmen"
        uword[] @shared wordarray1 = [1111,2222,3333,4444]
        uword[4] @shared wordarray2 = 12345
        uword[4] @shared wordzeroarray

        qq=4242             ; TODO should generate symbol not allocated address
        mapped = 42     ; TODO wrong VMASM code generated... should generate mapped memory address

        qq=global1
        qq=other.global2
        nested()
        main.start.nested.nested2()

        sub nested() {
            qq++
            txt.print("zzz")
            nested2()

            sub nested2() {
                txt.print("zzz2")
                qq++
            }
        }
    }
}

other {

    uword global2 = 9999

}
