%import textio
%zeropage basicsafe

main {
    sub start() {
        pointer @shared ptr = 777777
        txt.print_ub(sizeof(ptr))       ; expected 4
        txt.nl()
        txt.print_ub(sizeof(pointer))   ; expected 4
        txt.nl()
    }
}
