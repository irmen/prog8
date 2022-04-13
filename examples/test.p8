%import textio
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {

        word[] values = [1111, -222, -9999, 88, 20222, 0, 0, 1111]
        word[] values2 = [0,0,0,0,0,1,0,0,0]
        txt.print_w(max(values))
        txt.nl()
        txt.print_w(min(values))
        txt.nl()
        txt.print_w(sum(values))
        txt.nl()
        txt.print_ub(any(values))
        txt.nl()
        txt.print_ub(any(values2))
        txt.nl()
        txt.print_ub(all(values))
        txt.nl()

;        uword other = $fe4a
;        uword value = $ea31
;        uword[] warray = [$aa44, $bb55, $cc66]
;        ubyte upperb = msb(value)
;        ubyte lowerb = lsb(value)
;        txt.print_ubhex(upperb, true)
;        txt.print_ubhex(lowerb, false)
;        txt.nl()
;        value = mkword(upperb, lowerb)
;        txt.print_uwhex(value, true)
;        txt.nl()
;        upperb = msb(warray[1])
;        lowerb = lsb(warray[1])
;        txt.print_ubhex(upperb, true)
;        txt.print_ubhex(lowerb, false)
;        txt.nl()
;        ubyte index=1
;        upperb = msb(warray[index])
;        lowerb = lsb(warray[index])
;        txt.print_ubhex(upperb, true)
;        txt.print_ubhex(lowerb, false)
;        txt.nl()
;        swap(other, value)
;        txt.print_uwhex(value,true)
;        txt.nl()
;        txt.nl()
;
;        pokew($1000, $ab98)
;        txt.print_ubhex(@($1000),true)
;        txt.print_ubhex(@($1001),false)
;        txt.nl()
;        txt.print_uwhex(peekw($1000),true)
;        txt.nl()
;        swap(@($1000), @($1001))
;        txt.print_uwhex(peekw($1000),true)
;        txt.nl()
;        swap(warray[0], warray[1])
;        txt.print_uwhex(warray[1],true)
;        txt.nl()

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
