%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte x = 10
        ubyte y = 2
        txt.print_ub(5<x and x<=20)
        txt.nl()
        txt.print_ub(5<x and x<=9)
        txt.nl()
        txt.print_ub(5<x<=9)
        txt.nl()
        txt.print_ub(5<(x-y)<=9<y)
        txt.nl()
        txt.print_ub(5<(x-y)<=9<(y+40))
        txt.nl()
    }
}
