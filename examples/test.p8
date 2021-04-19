%import textio
%zeropage basicsafe
%import test_stack

main {

    sub start() {
        txt.print("^\n")
        txt.print("_\n")
        txt.print("{\n")
        txt.print("}\n")
        txt.print("|\n")
        txt.print("\\\n")
    }
}

