%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print_bool(sys.cpu_is_65816())
    }
}
