%import textio
%zeropage basicsafe
%option no_sysinit

main {
        sub start() {
            str  message = "a"

            txt.print(message)
            txt.nl()
            message[0] = '@'
            txt.print(message)
            txt.nl()
        }
}
