%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        old()
        new()
    }

    sub old() {
;        sys.push(99)
;        sys.pushw(2222)
;        sys.pushl(66663333)
;        floats.push(99.7777)
;        cx16.r0++
;
;        txt.print_f(floats.pop())
;        txt.spc()
;        txt.print_l(sys.popl())
;        txt.spc()
;        txt.print_uw(sys.popw())
;        txt.spc()
;        txt.print_ub(sys.pop())
;        txt.nl()
;
;        cx16.r0L = 42
;        cx16.r1 = 9999
;        cx16.r2r3sl = 44445555
;        float @shared fl = 33.8888
;
;        sys.push(cx16.r0L)
;        sys.pushw(cx16.r1)
;        sys.pushl(cx16.r2r3sl)
;        floats.push(fl)
;        cx16.r0++
;
;        txt.print_f(floats.pop())
;        txt.spc()
;        txt.print_l(sys.popl())
;        txt.spc()
;        txt.print_uw(sys.popw())
;        txt.spc()
;        txt.print_ub(sys.pop())
;        txt.nl()
    }

    sub new() {
        push(99)
        pushw(2222)
        pushl(66663333)
        pushf(99.7777)
        cx16.r0++

        txt.print_f(popf())
        txt.spc()
        txt.print_l(popl())
        txt.spc()
        txt.print_uw(popw())
        txt.spc()
        txt.print_ub(pop())
        txt.nl()

        cx16.r0L = 42
        cx16.r1 = 9999
        cx16.r2r3sl = 44445555
        float @shared fl = 33.8888

        push(cx16.r0L)
        pushw(cx16.r1)
        pushl(cx16.r2r3sl)
        pushf(fl)
        cx16.r0++

        txt.print_f(popf())
        txt.spc()
        txt.print_l(popl())
        txt.spc()
        txt.print_uw(popw())
        txt.spc()
        txt.print_ub(pop())
        txt.nl()
    }
}
