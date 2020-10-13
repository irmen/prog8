%import textio
%import syslib
%import floats
%zeropage basicsafe


main {

    sub start() {
        ubyte xx = 0
        repeat 254 {
            txt.chrout('1')
            xx++
        }
        txt.print_ub(xx)
        txt.chrout('\n')

        xx=0

        repeat 255 {
            txt.chrout('2')
            xx++
        }
        txt.print_ub(xx)
        txt.chrout('\n')

        xx=0
        repeat 256 {                ; TODO generates faulty code, loop is never executed at all
            txt.chrout('3')
            xx++
        }
        txt.print_ub(xx)
        txt.chrout('\n')

        xx=0
        repeat 257 {            ; TODO generates invalid 0-check code at start
            txt.chrout('4')
            xx++
        }
        txt.print_ub(xx)
        txt.chrout('\n')

        ubyte bb

        repeat bb {
            xx++
        }

        uword ww

        repeat ww {
            xx++
        }
    }

    sub rec2() {
        rec3()
    }

    sub rec3() {
        rec4()
    }

    sub rec4() {
        rec2()
    }

    sub derp() {
        repeat {
            derp()
        }
        if true {
            derp()
        } else {
            derp()
        }

        do {
            derp()
        } until true

        while true {
            derp()
        }
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



