%import textio

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        txt.clear_screen()
        txt.print("Welcome to a prog8 pixel shader :-)\n")
        uword ww = 0
        ubyte bc
        uword wc

        for bc in "irmen" {
            txt.chrout(bc)
            ww++
        }
        txt.print_uw(ww)    ; 5
        txt.nl()

        for bc in [10,11,12] {
            txt.print_ub(bc)
            txt.spc()
            ww++
        }
        txt.print_uw(ww)        ; 8
        txt.nl()
        txt.nl()

        for wc in [4097,8193,16385] {
            txt.print_uw(wc)
            txt.spc()
            ww++
        }
        txt.print_uw(ww)        ; 11
        txt.nl()

        for bc in 10 to 20 step 3 {
            ; 10,13,16,19
            txt.print_ub(bc)
            txt.spc()
            ww++
        }
        txt.print_uw(ww)        ; 15
        txt.nl()

        for bc in 30 to 10 step -4 {
            ; 30,26,22,18,14,10,6,2
            txt.print_ub(bc)
            txt.spc()
            ww++
        }
        txt.print_uw(ww)    ; 23
        txt.nl()


        sys.exit(99)


        ; the "pixelshader":
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
