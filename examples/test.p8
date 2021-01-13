%import textio
%import string
%zeropage basicsafe
%option no_sysinit

main {


    sub start() {
        txt.print_uwhex(memory("a", 100), 1)
        txt.nl()
        txt.print_uwhex(memory("a", 200), 1)
        txt.nl()
        txt.print_uwhex(memory("a", 200), 1)
        txt.nl()
        txt.print_uwhex(memory("b", 200), 1)
        txt.nl()
    }

}
