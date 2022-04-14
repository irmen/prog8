%import textio
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {

;        uword ww = 100
;        uword vv
;        vv = ww+1
;        txt.print_uw(vv)
;        txt.nl()
;        vv = ww * 8
;        txt.print_uw(vv)
;        txt.nl()

        ; a "pixelshader":
        void syscall1(8, 0)      ; enable lo res creen
        ubyte shifter

        ; pokemon(1,0)

        repeat {
            uword xx
            uword yy = 0
            repeat 240 {
                xx = 0
                repeat 320 {
                    syscall3(10, xx, yy, xx*yy + shifter)   ; plot pixel
                    xx++
                }
                yy++
            }
            shifter+=4
        }
    }
}
