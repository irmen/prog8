%import textio
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {

    ubyte global = 42

    sub start() {
        uword begin = c64.RDTIM16()

        ubyte shift
        repeat 60 {
            ubyte yy
            for yy in 0 to 59 {
                ubyte xx
                for xx in 0 to 79 {
                    ubyte color = yy+xx+shift
                    txt.setcc2(xx,yy,81,color)       ; 356
                }
            }
            shift++
        }

        uword duration = c64.RDTIM16()-begin
        txt.print_uw(duration)
        txt.print("     \n")

        ; a "pixelshader":
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
