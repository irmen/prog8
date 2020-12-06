%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {
    sub start() {
        const ubyte size = 100

        ubyte[size+10] bytes

        txt.print("hello\n")
        ubyte dummy = bytes[0]

    }
}
