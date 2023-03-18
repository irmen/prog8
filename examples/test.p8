%import textio
%import test_stack
%zeropage basicsafe
%option no_sysinit

; $1e4 size

main {

  sub start() {
    test_stack.test()

    str name = "irmen"
    name[3] = 0
    if name==".asm" or name=="irm" or name==".src"
        txt.print("ok\n")
    else
        txt.print("fail\n")


    uword xx=4000
    ubyte a
    ubyte b
    ubyte c
    ubyte d

    cx16.r0 = peekw(a+xx+b+c+d)
    ; TODO @(a+xx+b+c+d) = cx16.r0L

;    if cx16.r0L in "derp" {
;        xx++
;    }
;
;    xx = xx+(3*func(xx)+xx*2*cx16.r0L)
;    txt.print_uw(xx)
    test_stack.test()
  }

;  sub func(uword value) -> uword {
;    value ++
;    return value
;  }
}
