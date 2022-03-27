%import textio

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {

        byte bb = -9
        word ww = -1234
        if bb<0 {
            txt.print("bb <0!\n")
        } else {
            txt.print("comparison error!\n")
        }
        if ww<0 {
            txt.print("ww <0!\n")
        } else {
            txt.print("comparison error!\n")
        }
        if bb>0 {
            txt.print("comparison error!\n")
        } else {
            txt.print("bb not >0\n")
        }
        if ww>0 {
            txt.print("comparison error!\n")
        } else {
            txt.print("ww not >0\n")
        }

        txt.print_w(ww)
        txt.spc()
        ww <<= 3
        txt.print_w(ww)
        txt.nl()
        txt.print_b(bb)
        txt.spc()
        bb <<= 3
        txt.print_b(bb)
        txt.nl()

        sys.exit(99)

;        txt.clear_screen()
;        txt.print("Welcome to a prog8 pixel shader :-)\n")
;        uword ww = 0
;        ubyte bc
;        uword wc
;
;        for bc in "irmen" {
;            ; +5 -> 17
;            txt.chrout(bc)
;            ww++
;        }
;        txt.print_uw(ww)
;        txt.nl()
;
;        for wc in [1000,1111,1222] {
;            txt.print_uw(wc)
;            txt.spc()
;            ww++    ; +3 -> 20
;        }
;        txt.print_uw(ww)
;        txt.nl()
;
;        for bc in 10 to 20 step 3 {
;            ; 10,13,16,19 -> 4
;            ww++
;        }
;        txt.print_uw(ww)
;        txt.nl()
;        for bc in 30 to 10 step -4 {
;            ; 30,26,22,18,14,10,6,2 -> +8 -> 12
;            ww++
;        }
;        txt.print_uw(ww)
;        txt.nl()
;
;
;        sys.exit(99)


;        syscall1(8, 0)      ; enable lo res creen
;        ubyte shifter
;
;        shifter >>= 1
;
;        repeat {
;            uword xx
;            uword yy = 0
;            repeat 240 {
;                xx = 0
;                repeat 320 {
;                    syscall3(10, xx, yy, xx*yy + shifter)   ; plot pixel
;                    xx++
;                }
;                yy++
;            }
;            shifter+=4
;
;            txt.print_ub(shifter)
;            txt.nl()
;        }
    }
}
