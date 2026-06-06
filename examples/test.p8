%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        const ^^ubyte name = "irmen"
        txt.print(name)
    }
}
