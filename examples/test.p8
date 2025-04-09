%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name = "irmen!"
        txt.print(name)
        txt.nl()
        sys.memset(&name, len(name), 'a')
        txt.print(name)
        txt.nl()
        sys.memsetw(&name, len(name)/2, $4041)
        txt.print(name)
        txt.nl()
    }
}
