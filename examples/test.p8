%import textio
%import diskio
%zeropage basicsafe

main {

    sub start() {

        ubyte[100] cargohold

        struct SaveData {
            ubyte galaxy
            ubyte planet
            ubyte cargo0
            ubyte cargo1
            ubyte cargo2
            ubyte cargo3
            ubyte cargo4
            ubyte cargo5
            ubyte cargo6
            ubyte cargo7
            ubyte cargo8
            ubyte cargo9
            ubyte cargo10
            ubyte cargo11
            ubyte cargo12
            ubyte cargo13
            ubyte cargo14
            ubyte cargo15
            ubyte cargo16
            uword cash
            ubyte max_cargo
            ubyte fuel
        }
        SaveData savedata

        memcopy(&savedata.cargo0, cargohold, len(cargohold))      ; TODO fix compiler error about pointer

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
