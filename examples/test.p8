%import textio
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {

        ubyte ub = 234
        byte v1 = -10
        byte v2 = 20
        uword w3

        byte v3 = abs(v1) as byte
        txt.print_b(v3)
        txt.spc()
        v3 = abs(v2) as byte
        txt.print_b(v3)
        txt.spc()
        w3 = abs(v1)
        txt.print_uw(w3)
        txt.spc()
        w3 = abs(v2)
        txt.print_uw(w3)
        txt.spc()
        w3 = abs(ub)
        txt.print_uw(w3)
        txt.nl()

        txt.print_uw(abs(v1))
        txt.spc()
        txt.print_uw(abs(v2))
        txt.spc()
        txt.print_uw(abs(ub))
        txt.nl()

        word sw1 = -12345
        w3 = abs(sw1)
        txt.print_uw(w3)
        txt.spc()
        txt.print_uw(abs(sw1))
        txt.nl()

;        ; a "pixelshader":
;        void syscall1(8, 0)      ; enable lo res creen
;        ubyte shifter
;
;        ; pokemon(1,0)
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
;        }
    }
}
