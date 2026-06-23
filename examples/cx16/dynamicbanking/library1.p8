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
        txt.print("it was the best of times, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
    sub func2(uword value) -> uword {
        txt.print("it was the worst of times, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
    sub func3(uword value) -> uword {
        txt.print("it was the age of wisdom, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
    sub func4(uword value) -> uword {
        txt.print("it was the age of foolishness, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
}
