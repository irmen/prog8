%import textio
%zeropage basicsafe
%option no_sysinit

main {
    ubyte @shared @nozp value1 = 99
    ubyte @shared @requirezp value2 = 42
    sub start() {
        txt.print_ub(value1)
        txt.nl()
        txt.print_ub(value2)
        txt.nl()
    }
}
