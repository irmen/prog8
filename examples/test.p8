%import textio
%zeropage dontuse


; NOTE: meant to test to virtual machine output target (use -target vitual)

main {

;    sub ands(ubyte arg, ubyte b1, ubyte b2, ubyte b3, ubyte b4) -> ubyte {
;        return arg>b1 and arg>b2 and arg>b3 and arg>b4
;    }
;
;    sub ors(ubyte arg, ubyte b1, ubyte b2, ubyte b3, ubyte b4) -> ubyte {
;        return arg==b1 or arg==b2 or arg==b3 or arg==b4
;    }

;    sub mcCarthy() {
;        ubyte @shared a
;        ubyte @shared b
;
;        txt.print_ub(ands(10, 2,3,4,5))
;        txt.spc()
;        txt.print_ub(ands(10, 20,3,4,5))
;        txt.spc()
;        txt.print_ub(ors(10, 2,3,40,5))
;        txt.spc()
;        txt.print_ub(ors(10, 1,10,40,5))
;        txt.spc()
;    }

    sub crash () {
        uword eRef
        if eRef[3] and 10  {
          return
        }
    }

    sub start() {
        ; mcCarthy()

        ubyte value = 0
        ubyte size = 9
        ubyte[10] data = [11,22,33,4,5,6,7,8,9,10]
        uword bitmapbuf = &data

;        ; 11 22 33
;        txt.print_ub(bitmapbuf[0])
;        txt.spc()
;        txt.print_ub(bitmapbuf[1])
;        txt.spc()
;        txt.print_ub(bitmapbuf[2])
;        txt.nl()
;        rol(bitmapbuf[0])
;        rol(bitmapbuf[0])
;        txt.print_ub(bitmapbuf[0])  ; 44
;        txt.spc()
;        ror(bitmapbuf[0])
;        ror(bitmapbuf[0])
;        txt.print_ub(bitmapbuf[0])  ; 11
;        txt.nl()
;
;        ; 22 44 66
;        txt.print_ub(bitmapbuf[0]*2)
;        txt.spc()
;        txt.print_ub(bitmapbuf[1]*2)
;        txt.spc()
;        txt.print_ub(bitmapbuf[2]*2)
;        txt.nl()

        ubyte one = 1

        bitmapbuf[0] = one
        bitmapbuf[1] = one+one
        bitmapbuf[2] = one+one+one
        bitmapbuf[2] += 4
        bitmapbuf[2] -= 2
        bitmapbuf[2] -= 2
        swap(bitmapbuf[0], bitmapbuf[1])

        ; 1 2 3
        txt.print_ub(bitmapbuf[0])
        txt.spc()
        txt.print_ub(bitmapbuf[1])
        txt.spc()
        txt.print_ub(bitmapbuf[2])
        txt.nl()

        for value in data {
            txt.print_ub(value)
            txt.spc()
        }
        txt.nl()

        ; TODO why is this loop much larger in code than the one below.
        value = 0
        ubyte xx
        for xx in 0 to size-1 {
            value += bitmapbuf[xx]
        }
        txt.print_ub(value)     ; 45
        txt.nl()

        ; TODO this one is much more compact
        value = 0
        uword srcptr = bitmapbuf
        repeat size {
            value += @(srcptr)
            srcptr++
        }
        txt.print_ub(value)     ; 45
        txt.nl()

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
