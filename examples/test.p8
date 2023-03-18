%import textio
%import test_stack
%zeropage basicsafe
%option no_sysinit

; $1e4 size

main {

  sub start() {
    uword xx=4000
    ubyte a=11
    ubyte b=22
    ubyte c=33

;    cx16.r0 = peekw(xx+a+b+c)
;    cx16.r1 = peekw(xx+a+b+c+42)
;    pokew(xx+a+b+c, xx)
;    pokew(xx+a+b+c+42, xx)

    if a and a & $40 == 0
        cx16.r0++

;    if cx16.r0L in "derp" {
;        xx++
;    }
;
;    xx = xx+(3*func(xx)+xx*2*cx16.r0L)
;    txt.print_uw(xx)
  }

;  sub func(uword value) -> uword {
;    value ++
;    return value
;  }
}
