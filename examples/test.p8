%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte[10] uba = [1,2,3]
        bool[10] bba = [true, false, true]
    }
}
