%import textio
;%import test_stack
%zeropage basicsafe

; NOTE: meant to test to virtual machine output target (use -target vitual)

other {
    ubyte variable = 40
    ubyte var2=2

    sub func1(ubyte arg) -> ubyte {
        txt.print_ub(arg)
        txt.spc()
        return arg*var2
    }

    sub inliner() -> ubyte {
        return func1(variable)
    }

    sub inliner2() {
        txt.print_ub(22)
        return
    }

}

main $2000 {

    ubyte x=10
    ubyte y=20
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

        other.inliner2()
        other.inliner2()
        rnd()
        void other.inliner()
        void other.inliner()

        ubyte derp = other.inliner() * other.inliner()      ; TODO inline this  (was $207 bytes size)
        txt.print_ub(derp)
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
