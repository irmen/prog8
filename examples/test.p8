%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name = "irmen"
        txt.print(name)
    }
}
