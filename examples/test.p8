%import textio
%import string
%import compression
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        test_stack.test()
        example(function1, 42)
        example(function1, 99)
        example(function2, 42)
        example(function2, 99)
        test_stack.test()
        cx16.r0++

        sub function1(ubyte arg) {
            txt.print("function 1 arg=")
            txt.print_ub(arg)
            txt.nl()
        }

        sub function2(ubyte arg) {
            txt.print("function 2 arg=")
            txt.print_ub(arg)
            txt.nl()
        }

        sub example(uword function, ubyte value) {

            %asm {{
                lda  p8v_value
            }}

            call(function)
            cx16.r1 = function+10
            call(cx16.r1-10)
        }
    }
}

