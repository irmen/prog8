%import textio
%zeropage basicsafe
%option no_sysinit

main {

	byte[] xs1 = "foo1" ; <<<<<<<<<<<<
	str xs2 = "foo2" ; <<<<<<<<<<<<
	sub start() {
	    txt.print(xs1)
            stringopt()
	}

        sub stringopt() {
            str  message = "a"

            txt.print(message)
            txt.nl()
            message[0] = '@'
            txt.print(message)
            txt.nl()
        }
}
