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
        txt.print("it was the spring of hope, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
    sub func2(uword value) -> uword {
        txt.print("it was the winter of despair, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
    sub func3(uword value) -> uword {
        txt.print("we had everything before us, ")
        txt.print_uw(value)
        txt.nl()
        return value
    }
    sub func4(uword value) -> uword {
        txt.print("we had nothing before us...")
        txt.print_uw(value)
        txt.nl()
        return value
    }
}
