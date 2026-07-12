%import textio

main {
    sub start() {
        str name = "zzzzzzzz"

        txt.print_ub(sizeof(name))
        txt.nl()
        txt.print(foo("zz"))
        txt.nl()
    }

    sub foo(str zz) -> str {
        @(50000) = 2
        return "hello"
    }
}
