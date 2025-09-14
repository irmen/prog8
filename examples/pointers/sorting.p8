%import floats
%import strings
%import textio

%zeropage basicsafe

main{
    struct Country {
        str name
        float population        ; millions
        uword area              ; 1000 km^2
    }

    ^^Country[] countries = [
        ^^Country:["Indonesia", 285.72, 1904],
        ^^Country:["Congo", 112.83, 2344],
        ^^Country:["Vietnam", 101.60, 331],
        ^^Country:["United States", 347.28, 9372],
        ^^Country:["Iran", 92.42, 1648],
        ^^Country:["Turkey", 87.69, 783],
        ^^Country:["Brazil", 212.81, 8515],
        ^^Country:["Bangladesh", 175.69, 147],
        ^^Country:["Germany", 84.08, 357],
        ^^Country:["Japan", 123.10, 377],
        ^^Country:["India", 1463.87, 3287],
        ^^Country:["China", 1416.10, 9596],
        ^^Country:["Philippines", 116.79, 300],
        ^^Country:["Russia", 143.99, 17098],
        ^^Country:["Pakistan", 255.22, 881],
        ^^Country:["Nigeria", 237.53, 923],
        ^^Country:["Ethiopia", 135.47, 1104],
        ^^Country:["Mexico", 131.95, 1964],
        ^^Country:["Thailand", 71.62, 513],
        ^^Country:["Egypt", 118.37, 1002],
    ]

    sub start() {
        txt.lowercase()

        txt.print("UNSORTED:\n")
        dump()

        sort_by_population()
        txt.print("\nSORTED BY POPULATION:\n")
        dump()

        sort_by_area()
        txt.print("\nSORTED BY AREA:\n")
        dump()

        sort_by_name()
        txt.print("\nSORTED BY NAME:\n")
        dump()
    }

    sub sort_by_name() {
        ; stupid slow bubble sort
        ubyte n = len(countries)
        do {
            ubyte newn=0
            ubyte i
            for i in 1 to n-1 {
                if strings.compare(countries[i-1].name, countries[i].name) > 0 {
                    swap(i, i-1)
                    newn = i
                }
            }
            n = newn
        } until n<=1
    }

    sub sort_by_population() {
        ; stupid slow bubble sort
        ubyte n = len(countries)
        do {
            ubyte newn=0
            ubyte i
            for i in 1 to n-1 {
                if countries[i-1].population < countries[i].population {
                    swap(i, i-1)
                    newn = i
                }
            }
            n = newn
        } until n<=1
    }

    sub sort_by_area() {
        ; stupid slow bubble sort
        ubyte n = len(countries)
        do {
            ubyte newn=0
            ubyte i
            for i in 1 to n-1 {
                if countries[i-1].area < countries[i].area {
                    swap(i, i-1)
                    newn = i
                }
            }
            n = newn
        } until n<=1
    }

    sub swap(ubyte i, ubyte j) {
        ^^Country temp = countries[i]
        countries[i] = countries[j]
        countries[j] = temp
    }

    sub dump() {
        txt.print("name           pop.(millions)  area (1000 km^2)\n")
        txt.print("-------------- --------------- ----------------\n")

        ^^Country c
        for c in countries {
            if c==0
                break
            txt.print(c.name)
            txt.column(15)
            txt.print_f(c.population)
            txt.column(31)
            txt.print_uw(c.area)
            txt.nl()
        }
    }
}

