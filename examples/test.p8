%import textio
%zeropage basicsafe

main {
    alias print = txt.print_ub

    sub start() {
        print(42)
    }
}
