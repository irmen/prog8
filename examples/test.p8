%import textio
%import diskio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {

        txt.print("saving...")
        diskio.delete(8, "data.bin")
        if diskio.save(8, "data.bin", $0400, 40*25) {
            txt.clear_screenchars('*')
            txt.print("loading...")
            uword size = diskio.load(8, "data.bin", $0400)
            txt.print_uwhex(size, true)
            txt.chrout('\n')
        } else txt.print("io error\n")

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
