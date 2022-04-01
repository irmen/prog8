%import textio
%zeropage basicsafe

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {

    sub start() {

        txt.print("keyboard api test\n")

        cx16.kbdbuf_put('l')
        cx16.kbdbuf_put('o')
        cx16.kbdbuf_put('a')
        cx16.kbdbuf_put('d')
        cx16.kbdbuf_put(13)

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
