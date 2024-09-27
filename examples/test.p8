%import textio
%zeropage dontuse
%option no_sysinit

main {
    uword @shared variable
    sub start() {
        txt.print("hello!\n")
        txt.print_uw(variable)
        txt.nl()
        sys.exit3(1,2,3,false)
    }
}
