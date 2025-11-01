%import textio
%import strings
%zeropage basicsafe


main {

    sub start() {
        str n1 = "the quick brown fox"

        txt.print(n1)
        txt.nl()
        txt.print_ub(len(n1))
        txt.nl()
        n1[7]=0
        txt.print_ub(strings.length(n1))
        txt.nl()
        txt.print_ub(sizeof(n1))
        txt.nl()
        txt.print_ub(sizeof("zzzz"))
        txt.nl()
    }
}
