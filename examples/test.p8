%import textio
%zeropage basicsafe

main {
    sub start() {
        str[] names = ["irmen", "de", "jong"]
        uword zz = names[1]
        txt.print(names[1])
    }
}
