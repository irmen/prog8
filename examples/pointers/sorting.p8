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
        ["Indonesia", 285.72, 1904],
        ["Congo", 112.83, 2344],
        ["Vietnam", 101.60, 331],
        ["United States", 347.28, 9372],
        ["Iran", 92.42, 1648],
        ["Turkey", 87.69, 783],
        ["Brazil", 212.81, 8515],
        ["Bangladesh", 175.69, 147],
        ["Germany", 84.08, 357],
        ["Japan", 123.10, 377],
        ["India", 1463.87, 3287],
        ["China", 1416.10, 9596],
        ["Philippines", 116.79, 300],
        ["Russia", 143.99, 17098],
        ["Pakistan", 255.22, 881],
        ["Nigeria", 237.53, 923],
        ["Ethiopia", 135.47, 1104],
        ["Mexico", 131.95, 1964],
        ["Thailand", 71.62, 513],
        ["Egypt", 118.37, 1002],
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

