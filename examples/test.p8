%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        uword @shared string = 20000
        ubyte length

        while string[length]!=0 length++
    }
}

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
