%import textio
%import string
%zeropage basicsafe
%option no_sysinit

main {


    sub start() {
        str filename="?"*40
        txt.print("> ")
        ubyte il = txt.input_chars(filename)
        txt.print_ub(il)
        txt.nl()
        txt.print_ubhex(filename[0],1)
        txt.print_ubhex(filename[1],1)
        txt.print_ubhex(filename[2],1)
        txt.nl()
        txt.print(filename)
        txt.nl()

        txt.print("> ")
        il = txt.input_chars(filename)
        txt.print_ub(il)
        txt.nl()
        txt.print_ubhex(filename[0],1)
        txt.print_ubhex(filename[1],1)
        txt.print_ubhex(filename[2],1)
        txt.nl()
        txt.print(filename)
        txt.nl()
    }

}
