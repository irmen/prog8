%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {
    sub start() {
        uword foo
        uword bar

        uword[] arra = [1,2,3]
        str nom = "omnom"

        foo = &arra
        foo++
        foo = &nom
        foo++

        ding(nom)
        ding("sdfsdfd")

        txt.print("hello\n")
    }

    sub ding(uword ss) {
        txt.print(ss)
    }
}
