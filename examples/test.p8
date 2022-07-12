%import textio
%zeropage basicsafe

main {
    sub start() {
        bool bb2=true
        bool @shared bb = bb2 and true
        txt.print_ub(bb)
    }
}
