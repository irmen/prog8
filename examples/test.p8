%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {
    sub start() {
        uword foo = [1,2,3,4]      ; TODO SYNTAX ERROR
        uword bar = "sdfadsaf"      ; TODO SYNTAX ERROR



        txt.print("hello\n")
    }
}
