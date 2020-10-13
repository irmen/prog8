%import textio
%import diskio
%zeropage basicsafe


main {

    sub start() {
        if diskio.directory(8)
            txt.print("all good\n")
        else
            txt.print("there was an error\n")

        testX()

        if diskio.save(8, "irmen", $0400, 40*25)
            txt.print("saved ok\n")
        else
            txt.print("save error\n")

        diskio.status(8)

        txt.clear_screenchars('?')

        if diskio.load(8, "irmen", $0400)
            txt.print("load ok\n")
        else
            txt.print("load error\n")

        diskio.status(8)

        diskio.rename(8, "irmen", "newirmen")
        diskio.status(8)
        diskio.delete(8, "test")
        diskio.status(8)

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
