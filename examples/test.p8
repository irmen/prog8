%import c64flt
%zeropage basicsafe
%option enable_floats

main {

    ubyte abc


    sub start() {

        A=abc

        if A>0
            Y=abc       ; @todo gets prefixed with anon1_  but should not be because is found in other scope...

    }
}
