%import textio
%import floats
%zeropage dontuse

main {

label:
    sub start() {

        ubyte[6] ubs = 10 to 20 step 2
        ubyte[] ubs2 = 10 to 20 step 2
        float[6] fs = 10 to 20 step 2
        float[] fs2 = 10 to 20 step 2

        txt.print_ub(len(ubs))
        txt.nl()
        txt.print_ub(len(ubs2))
        txt.nl()
        txt.print_ub(len(fs))
        txt.nl()
        txt.print_ub(len(fs2))
        txt.nl()

        ubyte ix
        for ix in 0 to 5 {
            txt.print_ub(ubs2[ix])
            txt.spc()
        }
        txt.nl()
        for ix in 0 to 5 {
            floats.print_f(fs2[ix])
            txt.spc()
        }
        txt.nl()

    }
}
