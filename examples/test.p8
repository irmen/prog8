%import textio
%import floats
%zeropage floatsafe

main{
    struct Country {
        str name
        float population        ; millions
        uword area              ; 1000 km^2
        ubyte code
    }

    ^^bool @shared bptr = 2000
    ^^uword @shared wptr = 2000
    ^^float @shared fptr = 2000
    ^^Country @shared cptr = 2000
    ^^Country[10] countries
    bool b1, b2
    uword w1, w2
    float f1, f2

    sub start() {
        txt.print_uw(&bptr)
        txt.nl()

        booltest()
        wordtest()
        floattest()
        structtest()

        repeat{}
    }

    sub booltest() {
        b1 = bptr^^
        b2 = bptr[0]
        b1 = bptr[5]
        b2 = bptr[cx16.r0L]
        b1 = bptr[cx16.r0]
        b2 = bptr[cx16.r0L+5]
    }

    sub wordtest() {
        w1 = wptr^^
        w2 = wptr[0]
        w1 = wptr[5]
        w2 = wptr[cx16.r0L]
        w1 = wptr[cx16.r0]
        w2 = wptr[cx16.r0L+5]
    }

    sub floattest() {
        f1 = fptr^^
        f2 = fptr[0]
        f1 = fptr[5]
        f2 = fptr[cx16.r0L]
        f1 = fptr[cx16.r0]
        f2 = fptr[cx16.r0L+5]
    }

    sub structtest() {
        cx16.r0 = countries[0]
        cx16.r1 = countries[1]
        cx16.r0 = countries[cx16.r0L]
        cx16.r1 = countries[cx16.r0L+5]
    }
;
;        countries[0] = Country("Indonesia", 285.72, 1904, 42)
;        countries[1] = Country("Congo", 112.83, 2344, 55)
;
;        txt.print_uw(countries[0])
;        txt.spc()
;        txt.print_uw(countries[1])
;        txt.nl()
;
;        txt.print(countries[0].name)
;        txt.spc()
;        txt.print_uw(countries[0].area)
;        txt.spc()
;        txt.print_ub(countries[0].code)
;        txt.spc()
;;        txt.print_f(countries[0].population)
;        txt.nl()
;        txt.print(countries[1].name)
;        txt.spc()
;        txt.print_uw(countries[1].area)
;        txt.spc()
;        txt.print_ub(countries[1].code)
;        txt.spc()
;;        txt.print_f(countries[1].population)
;        txt.nl()
}

