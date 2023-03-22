%import textio
%import floats
%zeropage basicsafe

; Draw a mandelbrot in graphics mode (the image will be 256 x 200 pixels).
; NOTE: this will take an eternity to draw on a real c64. A CommanderX16 is a bit faster.
; even in Vice in warp mode (700% speed on my machine) it's slow, but you can see progress

; Note: this program is compatible with C64 and CX16.

main {
    sub start()  {
        float xsquared = 2.0
        float ysquared = 1.9
        uword w = 1
        ubyte h = 0

        str name = ".tx2"

        if name==".jpg" or name==".txt" or name==".gif" {
            txt.print("yes")
        }

;        if w==0 or xsquared+ysquared<4.0 {
;            txt.print("yes")
;        }

;        if w==0 {
;            txt.print("w=0 ")
;        }
;        if h==0 {
;            txt.print("h=0 ")
;        }
;        if w==0 or h==0 {
;            txt.print(" w or h=0")
;        }
    }
}
