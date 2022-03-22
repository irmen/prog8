%import textio

main {
    ubyte variable = 42

    sub start() {
        txt.print("value=")
        txt.print_ub(variable)

        variable = foo()
    }

    sub foo() -> ubyte {
        return variable+1
    }
}
