%address  $A000
%memtop   $C000
%output   library
%import textio

main {
    %jmptable (
        lib.func1,
        lib.func2,
        lib.func3,
        lib.func4
    )
    sub start() { }
}

lib {
    sub func1(uword value) -> uword {
        txt.print("it was the epoch of belief, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
    sub func2(uword value) -> uword {
        txt.print("it was the epoch of incredulity, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
    sub func3(uword value) -> uword {
        txt.print("it was the season of light, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
    sub func4(uword value) -> uword {
        txt.print("it was the season of darkness, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
}
