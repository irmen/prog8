%import textio
%zeropage dontuse
%option no_sysinit
%address $5000
%launcher none

main {
    sub start() {
        txt.print("address of start: ")
        txt.print_uwhex(&start, true)
        txt.nl()
    }
}
