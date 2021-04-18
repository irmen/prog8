%import textio
%zeropage basicsafe
%import test_stack

main {

blocklabel:

    sub start() {

label1:
        ubyte xx=99
        if 0==xx {
            txt.print("fout")
        }
        txt.print("loading ")
    }
}

