;%output elf
%import textio

main {
    sub start() {
        uword @shared uw = 55555
        uword @shared ub =222
        long @shared ll = 8888888
        txt.print_ub(sqrt(ub))
        txt.nl()
        txt.print_ub(sqrt(uw))
        txt.nl()
        txt.print_uw(sqrt(ll))
        txt.nl()
    }
}
