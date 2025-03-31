%import test_stack
%import textio
%zeropage dontuse
%option no_sysinit, romable

main {

    sub start()  {

        ubyte @shared bankno = 10

        extsub @bank bankno $a000 = routine1()
        extsub @bank 11 $a000 = routine2()

        routine1()
        routine2()

    }
}
