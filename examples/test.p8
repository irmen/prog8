%import floats
%import strings
%import textio

main{
    struct Country {
        str name
        float population        ; millions
        uword area              ; 1000 km^2
    }

    ^^Country[100] countries        ; won't be fully filled
    ubyte num_countries

    sub start() {

        ; because pointer array initialization is not supported yet, we have to add the countries in separate statements for now
        add(Country("Indonesia", 285.72, 1904))
        add(Country("Congo", 112.83, 2344))
        add(Country("Vietnam", 101.60, 331))

        txt.print(countries[0].name)
        txt.spc()
        txt.print(countries[1].name)
        txt.spc()
        txt.print(countries[2].name)
        txt.nl()
        txt.print(countries[0]^^.name)
        txt.spc()
        txt.print(countries[1]^^.name)
        txt.spc()
        txt.print(countries[2]^^.name)
        txt.nl()
    }

    sub add(^^Country c) {
        countries[num_countries] = c
        num_countries++
    }
}

