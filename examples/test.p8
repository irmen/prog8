%import textio
%import math
%zeropage basicsafe

main {
    sub start() {
        ubyte x,y,z = math.rnd()

        txt.print_ub(x)
        txt.nl()
        txt.print_ub(y)
        txt.nl()
        txt.print_ub(z)
        txt.nl()

        x=y=z=math.rnd()
        txt.print_ub(x)
        txt.nl()
        txt.print_ub(y)
        txt.nl()
        txt.print_ub(z)
        txt.nl()
    }
}
