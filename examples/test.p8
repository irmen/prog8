%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {

        txt.print("hello")
        txt.print("hello2")
        txt.print("hello")
        txt.print("hello2")
        txt.nl()
        txt.print("1")
        txt.print("1")
        txt.nl()
        txt.print("12")
        txt.print("12")
        txt.nl()
        txt.print("123")
        txt.print("123")
        txt.nl()

        derp.derp2()
        txt.chrout('!')

        sys.wait(3*60)
    }
}

derp {

    sub derp2 () {
        txt.print("hello")
        txt.print("hello2")
        txt.print("hello")
        txt.print("hello2")
        txt.nl()
        txt.print("1")
        txt.print("1")
        txt.nl()
        txt.print("12")
        txt.print("12")
        txt.nl()
        txt.print("123")
        txt.print("123")
        txt.nl()

    }
}
