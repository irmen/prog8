%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte[5] name = "irmen"
        txt.print(name)
    }
}
