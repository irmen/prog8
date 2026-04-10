%zeropage basicsafe
%option no_sysinit
%import textio

main {
    sub start() {
        ; CRASH ERROR: prog8.ast.FatalAstException: invalid replace
        uword @shared uwordpointer=2222
        txt.print_ub(uwordpointer[55][22])

        ; ERROR: wrongfully removing unused variable 'uwordpointer3' followed by 'argument 1 invalid argument type' on the second line
        uword uwordpointer3=3333
        txt.print_ub(uwordpointer3[55][22])

        ; THIS IS SUPPORTED AND WORKS (regular array indexing on uword ptr):
        uword uwordpointer4=2222
        txt.print_ub(uwordpointer4[55])
    }
}
