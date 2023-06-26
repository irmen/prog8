%import textio
%option no_sysinit
%zeropage dontuse

main {

    uword variable=55555

    sub start() {
        txt.print("active rambank=")
        txt.print_ub(cx16.getrambank())
        txt.print("\nvar addr=")
        txt.print_uwhex(&variable, true)
        txt.print("\nvalue=")
        txt.print_uw(variable)
        txt.print("\n(rambank 10) variable=")
        cx16.rambank(10)
        txt.print_uw(variable)
        txt.print("\n(rambank 2) variable=")
        cx16.rambank(2)
        txt.print_uw(variable)
        txt.print("\n(rambank 1) variable=")
        cx16.rambank(1)
        txt.print_uw(variable)
        txt.nl()
    }
}

