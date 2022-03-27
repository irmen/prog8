%import floats
%import textio

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        txt.clear_screen()
        txt.print("Welcome to a prog8 pixel shader :-)\n")
        float fl = 9.9
        fl = floats.pow(fl, 3.0)
        txt.print("fl=")
        floats.print_f(fl)
        txt.nl()
        sys.exit(99)


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
