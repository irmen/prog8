%import textio
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool @shared flag
        ubyte @shared bytevar
        uword @shared wordvar

;        cx16.r1=9999
;        flag = test(42)
;        cx16.r0L, flag = test2(12345, 5566, flag, -42)
;        cx16.r1, flag = test3()

        wordvar, bytevar, flag = test4()
        wordvar, bytevar, flag = test4()

        txt.print_uwhex(wordvar, true)
        txt.spc()
        txt.print_bool(flag)
        txt.spc()
        txt.print_ub(bytevar)
        txt.nl()
    }

    romsub $8000 = test(ubyte arg @A) -> bool @Pc
    romsub $8002 = test2(uword arg @AY, uword arg2 @R1, bool flag @Pc, byte value @X) -> ubyte @A, bool @Pc
    romsub $8003 = test3() -> uword @R1, bool @Pc


    asmsub test4() -> uword @AY, ubyte @X, bool @Pc {
        %asm {{
            lda  #<$11ee
            ldy  #>$11ee
            ldx  #42
            sec
            rts
        }}
    }
}
