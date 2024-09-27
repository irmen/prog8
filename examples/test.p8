%import textio
%zeropage dontuse

%output raw
%launcher none
%address $2000

main {
    uword @shared variable
    sub start() {
        txt.print("hello!\n")
        txt.print_uw(variable)
        txt.nl()
        sys.exit3(1,2,3,false)
    }
}
