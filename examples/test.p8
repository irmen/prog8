%import textio
%zeropage basicsafe

; NOTE: meant to test to virtual machine output target (use -target virtual)

main  {

    sub start() {

        ubyte a1 = 0
        ubyte a2 = 128



        ; petaxian roller.p8 line 49
        ; super large in new version, ok in 8.2....
        ; TODO cx16.r0 = a2 + 25 + (a1/40)
        ; txt.setcc( a1, a2 + 25 + (a1/40), 11,22)

        if a1 and a2 {
            a1++
        }

        if not a1 or not a2 {
            a1++
        }

        if a1!=99 and not a2 {
            a1++
        }

;        while a1 != a2 {
;            a1++
;        }
;
;        while a1!='\"'  {
;            a1++
;        }
;        do {
;            a1++
;        } until a1==99
;
;close_end:
;        a1++


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
