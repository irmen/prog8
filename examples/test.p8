%import textio
%import syslib
%zeropage basicsafe


main {

    sub start() {

        print_10s(1)
        txt.chrout('\n')
        print_10s(123)
        txt.chrout('\n')
        print_10s(54321)
        txt.chrout('\n')
        txt.chrout('\n')

        ubyte ww
        ww=1
        print_10s(ww)
        txt.chrout('\n')
        ww=123
        print_10s(ww)
        txt.chrout('\n')
        ww=255
        print_10s(ww)
        txt.chrout('\n')

        txt.print("\nCommands are:\n"+
            "buy   jump      inf     cash\n" +
            "sell  teleport  market  hold\n" +
            "fuel  galhyp    local   quit\n")

;        str name = "irmen de jong"
;        uword strptr = &name
;
;
;        txt.print_ub(strlen("1234"))
;        txt.chrout('\n')
;        txt.print_ub(strlen(name))
;        txt.chrout('\n')
;        txt.print_uwhex(strptr, 1)
;        txt.chrout('\n')
;        txt.print(strptr)
;        txt.chrout('\n')
;        txt.print_ub(strlen(strptr))
;        txt.chrout('\n')


    }

    asmsub print_10s(uword value @AY) clobbers(A, X, Y) {
        %asm {{
		    jsr  conv.uword2decimal
		    lda  conv.uword2decimal.decTenThousands
		    cmp  #'0'
		    beq  +
		    jsr  c64.CHROUT
+           lda  conv.uword2decimal.decThousands
		    cmp  #'0'
            beq  +
            jsr  c64.CHROUT
+           lda  conv.uword2decimal.decHundreds
		    cmp  #'0'
            beq  +
            jsr  c64.CHROUT
+           lda  conv.uword2decimal.decTens
            jsr  c64.CHROUT
            lda  #'.'
            jsr  c64.CHROUT
            lda  conv.uword2decimal.decOnes
            jsr  c64.CHROUT
            rts
        }}
    }


    asmsub testX() {
        %asm {{
            stx  _saveX
            lda  #13
            jsr  txt.chrout
            lda  _saveX
            jsr  txt.print_ub
            lda  #13
            jsr  txt.chrout
            ldx  _saveX
            rts
_saveX   .byte 0
        }}
    }
}



