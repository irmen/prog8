%import textio
%zeropage basicsafe

; NOTE: meant to test to virtual machine output target (use -target virtual)

main  {

    sub start() {
        ubyte a1 = 0
        ubyte a2 = 42
        ubyte a3 = 1

        if (a1==0)==0
            a3 = (a1==0)==0

        if (a1!=0)==0
            a3 = (a1!=0)==0

        if (a1==0)!=0
            a3 = (a1==0)!=0

        if (a1!=0)!=0
            a3 = (a1!=0)!=0

        if (a1==0) or (a2==0)
            a3 = (a1==0) or (a2==0)

        if (a1==0) and (a2==0)
            a3 = (a1==0) and (a2==0)

        if not a1 or not a2 or not(not(a3))
            a3=not a1 or not a2 or not(not(a3))

        if (a1==0) or (a2==0)
            a3 = (a1==0) or (a2==0)

        if (a1==0) and (a2==0)
            a3 = (a1==0) and (a2==0)

        txt.print_ub(a3)


;        ; a "pixelshader":
;        sys.gfx_enable(0)       ; enable lo res screen
;        ubyte shifter
;
;        repeat {
;            uword xx
;            uword yy = 0
;            repeat 240 {
;                xx = 0
;                repeat 320 {
;                    sys.gfx_plot(xx, yy, xx*yy + shifter as ubyte)
;                    xx++
;                }
;                yy++
;            }
;            shifter+=4
;        }
     }
}
