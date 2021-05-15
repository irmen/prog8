%import textio ; txt.*
%zeropage basicsafe
main {
    sub start() {

        str string1 = "stringvalue"
        str string2 = "stringvalue"
        str string3 = "stringvalue"

        txt.print("a")
        txt.print("a")
        txt.print("bb")
        txt.print("bb")
        txt.print("\n")
        txt.print("\n\n")
        txt.print(string1)
        txt.nl()
        txt.print(string2)
        txt.nl()
        txt.print(string3)
        txt.nl()
        txt.print("hello\n")
        txt.print("hello\n")
        txt.print("hello\n")
        txt.print("bye\n")
        txt.print("bye\n")
        txt.print("bye\n")

    }
}
