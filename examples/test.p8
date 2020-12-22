%import textio
%import diskio
%import floats
%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {

    sub start () {
        txt.print("hello\n")
    }
}
