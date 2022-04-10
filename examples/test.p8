%import textio
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {

        uword value = $ea31
        uword[] warray = [$aa44, $bb55, $cc66]
        ubyte upperb = msb(value)
        ubyte lowerb = lsb(value)
        txt.print_ubhex(upperb, true)
        txt.print_ubhex(lowerb, false)
        txt.nl()
        value = mkword(upperb, lowerb)
        txt.print_uwhex(value, true)
        txt.nl()
        upperb = msb(warray[1])
        lowerb = lsb(warray[1])
        txt.print_ubhex(upperb, true)
        txt.print_ubhex(lowerb, false)
        txt.nl()
        ubyte index=1
        upperb = msb(warray[index])
        lowerb = lsb(warray[index])
        txt.print_ubhex(upperb, true)
        txt.print_ubhex(lowerb, false)
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
