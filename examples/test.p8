%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte a = 1

        if a>4 or a<2 {
            a++
        }

        if a>=2 and a<4 {
            a++
        }

        txt.print_ub(a)  ; 3
    }
}
