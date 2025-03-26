%import test_stack
%import textio
%zeropage dontuse
%option no_sysinit, romable

main {

    sub start()  {
        uword[] array = [1111,2222,3333,4444]

        cx16.r5 = 1000
        cx16.r6 = 1010

        test_stack.test()
        for cx16.r2 in cx16.r5 to cx16.r6 {
            txt.print_uw(cx16.r2)
            txt.spc()
        }
        txt.nl()

        test_stack.test()
        for cx16.r0 in array {
            txt.print_uw(cx16.r0)
            txt.spc()
        }
        txt.nl()

        test_stack.test()
        cx16.r0L++
    }
}
