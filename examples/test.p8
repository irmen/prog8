%import buffers
%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        uword @shared ptr = $2000
        uword @shared index = 1000

        @($2000+1000) = 123
        @($2000+1001) = 124

        cx16.r0L = @(ptr+index)
        cx16.r1L = @(ptr+1001)

        txt.print_ub(cx16.r0L)
        txt.spc()
        txt.print_ub(cx16.r1L)
        txt.spc()

        cx16.r2L = ptr[index]
        cx16.r3L = ptr[1001]

        txt.print_ub(cx16.r2L)
        txt.spc()
        txt.print_ub(cx16.r3L)
        txt.spc()

        cx16.r0L = 200
        cx16.r1L = 201

        @(ptr+index) = cx16.r0L
        @(ptr+1001) = cx16.r1L

        txt.print_ub(@($2000+1000))
        txt.spc()
        txt.print_ub(@($2000+1001))
        txt.spc()

        cx16.r0L = 203
        cx16.r1L = 204

        ptr[index] = cx16.r0L
        ptr[1001] = cx16.r1L

        txt.print_ub(@($2000+1000))
        txt.spc()
        txt.print_ub(@($2000+1001))
        txt.spc()

;        txt.print_ub(ptr[index])
;        txt.nl()
;        ptr[index] = 123
;        txt.print_ub(ptr[index])
;        txt.nl()
    }
}



;
;main {
;    sub start() {
;        signed()
;        unsigned()
;    }
;
;    sub signed() {
;        byte @shared bvalue = -100
;        word @shared wvalue = -20000
;
;        bvalue /= 2     ; TODO should be a simple bit shift?
;        wvalue /= 2     ; TODO should be a simple bit shift?
;
;        txt.print_b(bvalue)
;        txt.nl()
;        txt.print_w(wvalue)
;        txt.nl()
;
;        bvalue *= 2
;        wvalue *= 2
;
;        txt.print_b(bvalue)
;        txt.nl()
;        txt.print_w(wvalue)
;        txt.nl()
;    }
;
;    sub unsigned() {
;        ubyte @shared ubvalue = 100
;        uword @shared uwvalue = 20000
;
;        ubvalue /= 2
;        uwvalue /= 2
;
;        txt.print_ub(ubvalue)
;        txt.nl()
;        txt.print_uw(uwvalue)
;        txt.nl()
;
;        ubvalue *= 2
;        uwvalue *= 2
;
;        txt.print_ub(ubvalue)
;        txt.nl()
;        txt.print_uw(uwvalue)
;        txt.nl()
;    }
;}
