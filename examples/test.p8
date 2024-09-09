%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        ubyte @shared x
        uword @shared w
        bool flag1, flag2

        sys.clear_carry()
        flag1 = onlyCarry()
        if flag1
            txt.print("1: ok\n")
        else
            txt.print("1: fail\n")

        x=1
        flag1 = onlyZero()
        if flag1
            txt.print("2: ok\n")
        else
            txt.print("2: fail\n")

        sys.clear_carry()
        flag1, x = carryAndByte()
        if flag1 and x==42
            txt.print("3: ok\n")
        else
            txt.print("3: fail\n")

        sys.clear_carry()
        flag1, w = carryAndWord()
        if flag1 and w==4242
            txt.print("4: ok\n")
        else
            txt.print("4: fail\n")

        sys.clear_carry()
        flag1, x, w = carryAndValues()
        if flag1 and x==99 and w==9999
            txt.print("5: ok\n")
        else
            txt.print("5: fail\n")

        x = 1
        sys.clear_carry()
        flag1, flag2 = onlyCarryAndZero()
        if flag1 and flag2
            txt.print("6: ok\n")
        else
            txt.print("6: fail\n")

        x = 1
        sys.clear_carry()
        flag1, flag2, x = carryAndZeroAndByte()
        if flag1 and flag2 and x==33
            txt.print("7: ok\n")
        else
            txt.print("7: fail\n")

        x = 1
        sys.clear_carry()
        flag1, flag2, x, w = carryAndNegativeAndByteAndWord()
        if flag1 and flag2 and x==55 and w==51400
            txt.print("8: ok\n")
        else
            txt.print("8: fail\n")
    }


    asmsub carryAndNegativeAndByteAndWord() -> bool @Pc, bool @Pn, ubyte @X, uword @AY {
        %asm {{
            ldx  #55
            lda  #200
            ldy  #200
            sec
            rts
        }}
    }

    asmsub carryAndZeroAndByte() -> bool @Pc, bool @Pz, ubyte @Y {
        %asm {{
            ldy  #33
            lda  #0
            sec
            rts
        }}
    }


    asmsub onlyCarryAndZero() -> bool @Pc, bool @Pz {
        %asm {{
            lda  #0
            sec
            rts
        }}
    }


    asmsub carryAndValues() -> bool @Pc, ubyte @X, uword @AY {
        %asm {{
            ldx  #99
            lda  #<9999
            ldy  #>9999
            sec
            rts
        }}
    }


    asmsub carryAndWord() -> bool @Pc, uword @AY {
        %asm {{
            lda  #<4242
            ldy  #>4242
            sec
            rts
        }}
    }

    asmsub carryAndByte() -> bool @Pc, ubyte @A {
        %asm {{
            lda  #42
            sec
            rts
        }}
    }

    asmsub onlyCarry() -> bool @Pc {
        %asm {{
            sec
            rts
        }}
    }

    asmsub onlyZero() -> bool @Pz {
        %asm {{
            lda  #0
            rts
        }}
    }
}
