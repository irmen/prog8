%import math
%import textio
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {

    sub start() {
        bool x
        ubyte y
        repeat 20 {
            x = math.rnd() & 1
            y = ((math.rnd()&1)!=0)
            txt.print_ub(x)
            txt.spc()
            txt.print_ub(y)
            txt.nl()
        }
    }
}
