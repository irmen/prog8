%import test_stack
%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {
        uword uw
        ubyte ub
        float ff

        uw = funcuw()
        ub = funcub()
        ff = funcff()
    }

    sub funcuw() -> uword {
        uword a=1111
        uword b=2222
        return a+b
    }

    sub funcub() -> ubyte {
        ubyte a=11
        ubyte b=22
        return a+b
    }

    sub funcff() -> float {
        float a=1111.1
        float b=2222.2
        return a+b
    }
}
