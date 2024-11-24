%import floats
%import math
%import textio
%zeropage basicsafe

main {
    sub start() {
        floats.print(floats.interpolate(0, 0, 10, 1000, 2000))
        txt.spc()
        txt.print_uw(math.interpolate(10, 10, 20, 100, 200))
        txt.nl()
        floats.print(floats.interpolate(2.22, 0, 10, 1000, 2000))
        txt.spc()
        txt.print_uw(math.interpolate(12, 10, 20, 100, 200))
        txt.nl()
        floats.print(floats.interpolate(5.0, 0, 10, 1000, 2000))
        txt.spc()
        txt.print_uw(math.interpolate(15, 10, 20, 100, 200))
        txt.nl()
        floats.print(floats.interpolate(10, 0, 10, 1000, 2000))
        txt.spc()
        txt.print_uw(math.interpolate(20, 10, 20, 100, 200))
        txt.nl()
    }
}
