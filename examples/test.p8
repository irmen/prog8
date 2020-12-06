%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {
    sub start() {
        uword uw
        ubyte ub1
        ubyte ub2

        uw = ub1 as uword + ub2     ; fairly ok asm  (TODO make it use stz on cx16)
        ub1++
        uw = ub1 + ub2          ; TODO horrible asm using the eval stack... fix

        uw *= 8     ; TODO is using a loop... unroll somewhat

        txt.print("hello\n")
    }
}
