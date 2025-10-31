%import textio
%import strings
%zeropage basicsafe


main {

    sub start() {
        str n1 = "irmen"
        str n2 = "de jong 12345678"

        ;;strings.copy(n2,n1)
        strings.ncopy(n2,n1,len(n1))
        txt.print(n1)
        txt.nl()

        n2[9]=0
        txt.print(n2)
        txt.nl()
        strings.nappend(n2, "the quick brown fox jumps over", 16)
        txt.print(n2)
        txt.nl()
        txt.print("12345678901234567890\n")
    }
}
