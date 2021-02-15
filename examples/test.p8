%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        txt.nl()
        txt.nl()
        txt.nl()


        txt.setcc2(0,0, 'a', $0f)
        txt.setcc2(1,0, 'a', $1f)
        txt.setcc2(2,0, 'a', $2f)
        txt.setcc2(3,0, 'a', $3f)
        txt.setcc2(4,0, 'a', $4f)
        txt.setcc2(5,0, 'a', $5f)
        txt.setcc2(6,0, 'a', $6f)
        txt.setcc2(7,0, 'a', $7f)
        txt.setcc2(8,0, 'a', $8f)
        txt.setcc2(9,0, 'a', $9f)
        txt.setcc2(10,0, 'a', $af)
        txt.setcc2(11,0, 'a', $bf)
        txt.setcc2(12,0, 'a', $cf)
        txt.setcc2(13,0, 'a', $df)
        txt.setcc2(14,0, 'a', $ef)
        txt.setcc2(15,0, 'a', $ff)


    }
}
