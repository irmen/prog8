%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte j1
        ubyte j2
        ubyte j3

        repeat {
            %asm {{
                lda  #0
                jsr  cx16.joystick_get
                sta  j1
                stx  j2
                sty  j3
            }}

            txt.print_ubbin(j1, false)
            txt.spc()
            txt.print_ubbin(j2, false)
            txt.spc()
            txt.print_ubbin(j3, false)

            txt.spc()
            uword qq = cx16.joystick_get2(0)
            txt.print_uwbin(qq, false)
            txt.nl()
        }
    }
}

