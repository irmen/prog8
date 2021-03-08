%import textio
%import floats
%import test_stack
%zeropage dontuse

; TODO fix float conversion crashes on Cx16  (ubyte as float,  uword as float)

main {
    sub start() {
        uword total=0
        uword xx
        float fl
        float fltotal=0.0
        ubyte ub = 22

        for xx in 0 to 100 {
            txt.print_uw(xx*xx)
            txt.chrout(',')
        }
        txt.nl()

        total = 0
        c64.SETTIM(0,0,0)
        repeat 5 {
            for xx in 1 to 255 {
                total += xx*xx
            }
        }
        txt.print_uw(total)
        txt.nl()
        txt.print_uw(c64.RDTIM16())
        txt.nl()
        txt.nl()
        test_stack.test()

;        fltotal=0.0
;        c64.SETTIM(0,0,0)
;        repeat 5 {
;            for xx in 1 to 255 {
;                fl = xx as float
;                ; fl = ub as float
;                fltotal = fl * fl
;            }
;        }
;
;        floats.print_f(fltotal)
;        txt.nl()
;        txt.print_uw(c64.RDTIM16())
;        txt.nl()
;        txt.nl()
;
;        fltotal=0.0
;        c64.SETTIM(0,0,0)
;        repeat 5 {
;            for xx in 1 to 255 {
;                fl = xx as float
;                ; fl = ub as float
;                fltotal = fl ** 2
;            }
;        }
;
;        floats.print_f(fltotal)
;        txt.nl()
;        txt.print_uw(c64.RDTIM16())
;        txt.nl()
;        txt.nl()

    }
}
