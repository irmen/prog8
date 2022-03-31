%import textio
%zeropage basicsafe

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        txt.clear_screen()

        txt.setcc2(10,3,'*',$12)
        txt.setcc2(11,3,'*',$02)
        txt.setcc2(12,3,'*',$10)
        txt.setcc2(10,4,'*',$d5)
        txt.setcc2(11,4,'*',$05)
        txt.setcc2(12,4,'*',$d0)

        sys.wait(100)
        cx16.screen_mode(0, false)
        sys.wait(100)


;        byte[] barr = [-1,-2,-3]
;        uword[] uwarr = [1111,2222,3333]
;
;        txt.print_b(barr[2])
;        txt.spc()
;        txt.print_uw(uwarr[2])
;        txt.nl()
;
;        barr[2] --
;        uwarr[2] --
;
;        txt.print_b(barr[2])
;        txt.spc()
;        txt.print_uw(uwarr[2])
;        txt.nl()
;
;        barr[2] ++
;        uwarr[2] ++
;
;        txt.print_b(barr[2])
;        txt.spc()
;        txt.print_uw(uwarr[2])
;        txt.nl()
;
;        sys.exit(99)


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
