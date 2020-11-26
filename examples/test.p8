%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        float fl
        word ww
        uword uw
        byte bb
        ubyte ub
        str string1 = "irmen"
        uword[] array = [1111,2222,3333]

        uw = string1 as uword
        txt.print_uwhex(uw,1)
        txt.chrout('\n')
        uw = array as uword
        txt.print_uwhex(uw,1)
        txt.chrout('\n')
        uw = name() as uword
        txt.print_uwhex(uw,1)
        txt.chrout('\n')
        uw = name()
        txt.print_uwhex(uw,1)
        txt.chrout('\n')


        test_stack.test()

    }

    sub name() -> str {
        return "irmen"
    }
}
