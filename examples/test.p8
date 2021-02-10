%import textio
%zeropage basicsafe

main {

    sub start() {
        txt.print("hello\n")
        txt.column(3)
        txt.print("hello2\n")
        txt.column(8)
        txt.print("hello3\n")
        txt.column(34)
        txt.print("hello4\n")
        txt.column(1)
        txt.print("hello5\n")
        txt.column(0)
        txt.print("hello6\n")
    }
}
