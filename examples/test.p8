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

    ^^Country @shared cptr = 2000
    ^^Country[10] countries
    ^^ubyte name1, name2
    float pop1, pop2
    uword area1, area2
    ubyte code1, code2

    sub start() {
        structname_array()
        structname_ptr()
        repeat{}
    }

    sub structname_array() {
        name1 = countries[0].name
        name2 = countries[1].name
        name1 = countries[cx16.r0L].name
        name2 = countries[cx16.r0L+5].name
    }

    sub structname_ptr() {
        name1 = cptr^^.name
        ;name1 = cptr[0].name        ; TODO fix compiler error about '.' expression
        name2 = cptr[1].name
        name1 = cptr[cx16.r0].name
        name2 = cptr[cx16.r0+5].name
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

