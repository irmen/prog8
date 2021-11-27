%import test_stack

main {

    sub start() {
        test_stack.test()

        uword @shared uw

;        sys.push(-22 as ubyte)
;        sys.push(44)
;        sys.pushw(-11234 as uword)
;        sys.pushw(12345)
;        sys.push(1)
;        sys.push(2)
;        ubyte @shared ub = sys.pop()
;        byte @shared bb = sys.pop() as byte
;        uw = sys.popw()
;        word @shared ww = sys.popw() as word
;        void sys.pop()
;        void sys.pop()

        routine2(uw, 11,22, true, 33)

        test_stack.test()

        repeat {
        }

    }

    asmsub routine2(uword num @AY, ubyte a1 @R1, ubyte a2 @R2, ubyte switch @Pc, ubyte a3 @X) {
        %asm {{
            adc #20
            rts
        }}
    }

}
