%import test_stack
%import textio
%import string
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        txt.print_ub(conv.any2uword("1"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword("11"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword("12345"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword("65501"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword("999999999"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword("%10101010"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword("$ff"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword("$ff99aa"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword("%ff"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword("abc"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword("$zzzz"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword(" 1234"))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()

        txt.print_ub(conv.any2uword(""))
        txt.chrout(':')
        txt.print_uw(cx16.r0)
        txt.nl()
        test_stack.test()
    }
}
