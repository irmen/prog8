%import textio
%zeropage basicsafe

main {
    struct Node {
        ^^Node next
    }

    sub start() {
        ^^Node node = 6000
        long @shared lvar       = 999999
        ^^long @shared lptr     = 5000
        ^^bool bptr
        ^^ubyte ubptr
        pokel(5000, 11223344)

        lptr^^ = 0
        lptr^^ = lvar
        lptr^^ = 82348234+lvar
        txt.print_l( lptr^^)
        lvar = lptr^^+1111111

        ^^Node next = 8888
        node.next = next

        bptr^^=false
        ubptr^^=0
    }
}
