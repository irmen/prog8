%zeropage basicsafe
%option no_sysinit
%import textio
%import verafx

main {
    sub start() {
        uword lower16 = verafx.mult(11111,9988)
        uword upper16 = cx16.r0
        txt.print_uwhex(upper16, true)   ; $069d5e9c  = 110976668
        txt.print_uwhex(lower16, false)
        txt.nl()
    }
}
