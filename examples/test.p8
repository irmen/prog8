%import textio
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {

  sub start() {
    test_stack.test()
    uword xx=32
    cx16.r0L = 3

    if cx16.r0L in "derp" {
        xx++
    }

    xx = xx+(3*func(xx)+xx*2*cx16.r0L)
    txt.print_uw(xx)
    test_stack.test()
  }

  sub func(uword value) -> uword {
    value ++
    return value
  }
}
