main {
    sub start() {
        struct Node {
            ubyte weight
        }
        ^^Node nodes
        nodes^^.zzz = 99
        cx16.r0L = nodes^^.zzz
        cx16.r0L = nodes[2].value
    }
}

;%import floats
;%import textio
;
;main{
;    struct Country {
;        str name
;        float population        ; millions
;        uword area              ; 1000 km^2
;    }
;
;    ^^Country[100] countries
;    ubyte num_countries
;
;    sub start() {
;
;        str[2] @shared names = [ "aaa", "bb"]
;
;        add(Country("India", 1463.87, 3287))
;        add(Country("China", 1416.10, 9596))
;        add(Country("United States", 347.28, 9372))
;        add(Country("Indonesia", 285.72, 1904))
;        add(Country("Pakistan", 255.22, 881))
;        add(Country("Nigeria", 237.53, 923))
;        add(Country("Brazil", 212.81, 8515))
;        add(Country("Bangladesh", 175.69, 147))
;        add(Country("Russia", 143.99, 17098))
;        add(Country("Ethiopia", 135.47, 1104))
;        add(Country("Mexico", 131.95, 1964))
;        add(Country("Japan", 123.10, 377))
;        add(Country("Egypt", 118.37, 1002))
;        add(Country("Philippines", 116.79, 300))
;        add(Country("Congo", 112.83, 2344))
;        add(Country("Vietnam", 101.60, 331))
;        add(Country("Iran", 92.42, 1648))
;        add(Country("Turkey", 87.69, 783))
;        add(Country("Germany", 84.08, 357))
;        add(Country("Thailand", 71.62, 513))
;
;        txt.print("UNSORTED:\n")
;        dump()
;
;        sort_by_population(countries, num_countries)
;        txt.print("SORTED BY POPULATION:\n")
;        dump()
;
;        sort_by_area(countries, num_countries)
;        txt.print("SORTED BY AREA:\n")
;        dump()
;    }
;
;    sub sort_by_population(^^Country cs, ubyte length) {
;
;    }
;
;    sub sort_by_area(^^Country cs, ubyte length) {
;
;    }
;
;    sub dump() {
;        txt.print("name           pop.(millions)  area (1000 km^2)\n")
;        txt.print("-------------- --------------- ----------------\n")
;        ubyte ci
;        for ci in 0 to num_countries-1 {
;            ^^Country cc = countries[ci]
;            txt.print(cc.name)
;            txt.column(15)
;            txt.print_f(cc.population)
;            txt.column(31)
;            txt.print_uw(cc.area)
;            txt.nl()
;        }
;    }
;
;    sub add(^^Country c) {
;        countries[num_countries] = c
;        num_countries++
;    }
;}
;
