%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        long[] foo = [1,2,3]
        long bar = 0
        ubyte @shared index = 0
        bar = 4
        txt.print("bar: ") txt.print_l(bar) txt.nl()
        txt.print("foo[0]: ") txt.print_l(foo[0]) txt.nl()
        txt.print("bar < foo[0]: ") txt.print_bool(bar < foo[0]) txt.nl()
        txt.print("bar <= foo[0]: ") txt.print_bool(bar <= foo[0]) txt.nl()
        txt.print("bar > foo[0]: ") txt.print_bool(bar > foo[0]) txt.nl()
        txt.nl()

        txt.print("variable indexed.\nbar: ") txt.print_l(bar) txt.nl()
        txt.print("foo[0]: ") txt.print_l(foo[index]) txt.nl()
        txt.print("bar < foo[0]: ") txt.print_bool(bar < foo[index]) txt.nl()
        txt.print("bar <= foo[0]: ") txt.print_bool(bar <= foo[index]) txt.nl()
        txt.print("bar > foo[0]: ") txt.print_bool(bar > foo[index]) txt.nl()
        txt.nl()

        long baz = foo[0]
        txt.print("baz: ") txt.print_l(baz) txt.nl()
        txt.print("bar < baz: ") txt.print_bool(bar < baz) txt.nl()
        txt.print("bar <= baz: ") txt.print_bool(bar <= baz) txt.nl()
        txt.print("bar > baz: ") txt.print_bool(bar > baz) txt.nl()
    }
}


;main {
;
;    sub start() {
;        printf([1111,2,3,4444])
;        printf([1111,2,3,-4444])
;        printf2([1111,2,3,4444])
;        printf2([1111,2,3,-4444])
;
;        sub printf(uword ptr) {
;            ptr++
;        }
;
;        sub printf2(^^uword ptr) {
;            ptr++
;        }
;    }
;}

;main {
;    sub start() {
;        ubyte[256] @shared array1
;        ubyte[256] @shared array2
;        ubyte[256] @shared array3
;
;        setvalues()
;        readvalues()
;        printvalues()
;
;        sub setvalues() {
;            poke(&array2 + 255, 99)
;            poke(&array2 + 256, 88)
;            poke(&array2 + $3000, 77)
;        }
;
;        sub readvalues() {
;            %ir {{
;loadm.b r1007,main.start.array2+255
;storem.b r1007,$ff02
;load.w r1009,main.start.array2
;add.w r1009,#$0100
;loadi.b r1008,r1009
;storem.b r1008,$ff04
;load.w r1011,main.start.array2
;add.w r1011,#$3000
;loadi.b r1010,r1011
;storem.b r1010,$ff06
;return
;            }}
;;            cx16.r0L = array2[255]
;;            cx16.r1L = @(&array2 + 256)
;;            cx16.r2L = @(&array2 + $3000)
;        }
;
;        sub printvalues() {
;            txt.print_ub(cx16.r0L)
;            txt.spc()
;            txt.print_ub(cx16.r1L)
;            txt.spc()
;            txt.print_ub(cx16.r2L)
;        }
;    }
;}
