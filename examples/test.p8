%import textio
%zeropage basicsafe
%import test_stack

main {
    sub start() {
        ubyte xx=99
        if 0==xx {
            txt.print("fout")
        }
        txt.print("loading ")
    }
}

