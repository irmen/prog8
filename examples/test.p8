%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte[5,3] multiarray

        str name = "irmen"
        txt.print(name)
    }
}
