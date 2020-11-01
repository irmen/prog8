%import textio
%import floats
%import syslib
%zeropage basicsafe


; Note: this program is compatible with C64 and CX16.

main {

    sub start() {
        const uword ADDR = $0400

        memset(ADDR, 40*25, 100)
        memsetw(ADDR, 20*10, $3031)
        memcopy(ADDR, ADDR+40*12, 20*10*2)
        ;memcopy(ADDR, ADDR+40*12, 255)

        testX()
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
