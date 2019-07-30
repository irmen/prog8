%import c64flt
%zeropage basicsafe
%option enable_floats

main {

    ubyte abc


    sub start() {

        A=abc
        ubyte zzz

        repeat {
            uword wvar
            Y=abc
            Y=zzz
            wvar=99
        } until 99

        repeat {
            uword wvar
            Y=abc
            Y=zzz
            wvar=99
        } until 99

        if A>0 {
            uword wvar
            Y=abc
            Y=zzz
            wvar=99
        }


    }
}
