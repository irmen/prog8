%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword @shared address = $C09F
        ubyte @shared bank = 10
        uword @shared argument = $1234

        void callfar(bank, address, argument)
        void callfar(10, $C09F, argument)
    }
}
