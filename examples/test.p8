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

    ^^Country @shared cptr = 20000
    ^^Country @shared @nozp cptr2 = 20000
    ^^Country @shared @requirezp cptr3 = 20000
    word @shared @nozp nonzeropage
    word @shared idontcare
    ^^ubyte @shared ubptr = 20000

    sub start() {
        ubptr^^ += 10
        cptr.code = 20
        cptr.code += 10
;        cptr.name = "name1"
;        cptr.population = 1.111
;        cptr.area = 11111
;        cptr ++
;        cptr.name = "name2"
;        cptr.population = 2.222
;        cptr.area = 22222
;        cptr = 20000
;
;        dump()
;        printnames()
    }

    sub printnames() {
        txt.print(cptr[0].name)
        txt.nl()
        txt.print(cptr[1].name)
        txt.nl()
    }

    sub dump() {
        txt.print(cptr[0].name)
        txt.spc()
        txt.print_f(cptr[0].population)
        txt.spc()
        txt.print_uw(cptr[0].area)
        txt.nl()
        txt.print(cptr[1].name)
        txt.spc()
        txt.print_f(cptr[1].population)
        txt.spc()
        txt.print_uw(cptr[1].area)
        txt.nl()
    }
}

