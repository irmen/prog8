%import strings
%import textio
%zeropage basicsafe

main {
    sub start() {
        str source="the quick fox"
        str target="?"*40

        txt.print(source)
        txt.print("<\n")

        strings.slice(source, 4, 5, target)
        txt.print(target)
        txt.print("<\n")

        strings.right(source, 3, target)
        txt.print(target)
        txt.print("<\n")
    }
}
