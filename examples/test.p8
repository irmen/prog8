%import textio
;%import test_stack
%zeropage basicsafe


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

    sub start() {
        ; mcCarthy()
        ;test_stack.test()

        ubyte value = 0
        ubyte one = 1
        ubyte two = 2
        ubyte[10] data = [11,22,33,4,5,6,7,8,9,10]
        uword bitmapbuf = &data

        ; 11 22 33
        txt.print_ub(bitmapbuf[0])
        txt.spc()
        txt.print_ub(bitmapbuf[1])
        txt.spc()
        txt.print_ub(bitmapbuf[2])
        txt.nl()
        rol(bitmapbuf[0])
        rol(bitmapbuf[0])
        txt.print_ub(bitmapbuf[0])  ; 44
        txt.spc()
        ror(bitmapbuf[0])
        ror(bitmapbuf[0])
        txt.print_ub(bitmapbuf[0])  ; 11
        txt.nl()

        ; 22 44 66
        txt.print_ub(bitmapbuf[0]*2)
        txt.spc()
        txt.print_ub(bitmapbuf[1]*2)
        txt.spc()
        txt.print_ub(bitmapbuf[2]*2)
        txt.nl()

        value = one+one+one+one+one
        txt.print_ub(value)     ; 5
        txt.nl()

        bitmapbuf[0] = one
        bitmapbuf[1] = one+one
        bitmapbuf[2] = one+one+one
        bitmapbuf[2] += 4
        bitmapbuf[2] -= 2
        bitmapbuf[2] -= 2

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

        ;test_stack.test()


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
