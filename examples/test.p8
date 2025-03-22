%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit, romable

main {

    sub start()  {
        &ubyte[100] @shared array = $a000
        str name = "irmen1234567890" * 16 + "abcdefghijklmno"

        txt.print_ub(len(name))
        txt.nl()

        test_stack.test()

        for cx16.r0L in name {
            txt.chrout(cx16.r0L)
        }

        test_stack.test()
        cx16.r0++

;        cx16.r0L = @(ptr)
;
;        @(ptr)++
;
;        @(ptr)+=10
    }
}
