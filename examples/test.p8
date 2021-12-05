%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str text = "\x00\xff\u0041A"
        txt.print(text)
        txt.nl()
        txt.print_ub(text[0])
        txt.spc()
        txt.print_ub(text[1])
        txt.spc()
        txt.print_ub(text[2])
        txt.spc()
        txt.print_ub(text[3])
        txt.nl()
    }
}
