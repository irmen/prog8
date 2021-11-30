%import textio
%import test_stack
%zeropage dontuse

main {

    sub start() {
        ubyte @shared dummy
        word b1 = 1111
        byte b2 = 22
        word b3 = 3333
        dummy++
        func(-b1,-b2,-b3)
    }

    sub printz(word a1, byte a2, word a3) {
        txt.print_w(a1)
        txt.spc()
        txt.print_b(a2)
        txt.spc()
        txt.print_w(a3)
        txt.nl()
    }
    asmsub func(word a1 @XY, byte a2 @A, word a3 @R0) {
        %asm {{
            stx  printz.a1
            sty  printz.a1+1
            sta  printz.a2
            lda  cx16.r0
            sta  printz.a3
            lda  cx16.r0+1
            sta  printz.a3+1
            jmp  printz
        }}
    }
}
