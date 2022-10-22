%import textio
%import math
%import floats
%zeropage basicsafe

main {

    sub printnumbers() {
        txt.print_ub(math.rnd())
        txt.spc()
        txt.print_ub(math.rnd())
        txt.spc()
        txt.print_ub(math.rnd())
        txt.nl()
        txt.print_uw(math.rndw())
        txt.spc()
        txt.print_uw(math.rndw())
        txt.spc()
        txt.print_uw(math.rndw())
        txt.nl()
        floats.print_f(floats.rndf())
        txt.spc()
        floats.print_f(floats.rndf())
        txt.spc()
        floats.print_f(floats.rndf())
        txt.nl()
    }


    sub start() {
        txt.print("initial:\n")
        math.rndseed($a55a, $7653)
        floats.rndseedf(11,22,33)
        printnumbers()
        txt.print("\nsame seeds:\n")
        math.rndseed($a55a, $7653)
        floats.rndseedf(11,22,33)
        printnumbers()
        txt.print("\ndifferent seeds:\n")
        math.rndseed($1234, $5678)
        floats.rndseedf(44,55,66)
        printnumbers()
        txt.nl()
    }
}
