%import c64utils
%option enable_floats

~ main {

    sub start()  {

        ubyte ub1
        ubyte ub2
        byte b1
        byte b2
        uword uw1
        uword uw2
        word  w1
        word  w2
        float f1
        float f2
        float f3

        ; byte and ubyte output via print are all OK!
        c64scr.print_byte_decimal(0)
        c64.CHROUT(' ')
        c64scr.print_byte_decimal(123)
        c64.CHROUT(' ')
        c64scr.print_byte_decimal(b2ub(-99))  ; @todo allow signed values for register
        c64.CHROUT('\n')
        c64scr.print_ubyte_decimal(0)
        c64.CHROUT(' ')
        c64scr.print_ubyte_decimal0(0)
        c64.CHROUT(' ')
        c64scr.print_ubyte_decimal(55)
        c64.CHROUT(' ')
        c64scr.print_ubyte_decimal0(55)
        c64.CHROUT(' ')
        c64scr.print_ubyte_decimal(254)
        c64.CHROUT(' ')
        c64scr.print_ubyte_decimal0(254)
        c64.CHROUT('\n')
        c64scr.print_ubyte_hex(0, 0)
        c64.CHROUT(' ')
        c64scr.print_ubyte_hex(1, 0)
        c64.CHROUT(' ')
        c64scr.print_ubyte_hex(0, $99)
        c64.CHROUT(' ')
        c64scr.print_ubyte_hex(1, $99)
        c64.CHROUT(' ')
        c64scr.print_ubyte_hex(0, $ea)
        c64.CHROUT(' ')
        c64scr.print_ubyte_hex(1, $ea)
        c64.CHROUT('\n')

        ; print_uword_decimal are all OK!
        c64scr.print_uword_decimal(0)
        c64.CHROUT(' ')
        c64scr.print_uword_decimal0(0)
        c64.CHROUT(' ')
        c64scr.print_uword_decimal(987)
        c64.CHROUT(' ')
        c64scr.print_uword_decimal0(987)
        c64.CHROUT(' ')
        c64scr.print_uword_decimal(55666)
        c64.CHROUT(' ')
        c64scr.print_uword_decimal0(55666)
        c64.CHROUT('\n')
        c64scr.print_uword_hex(0, 0)
        c64.CHROUT(' ')
        c64scr.print_uword_hex(1, 0)
        c64.CHROUT(' ')
        c64scr.print_uword_hex(0, $99)
        c64.CHROUT(' ')
        c64scr.print_uword_hex(1, $99)
        c64.CHROUT(' ')
        c64scr.print_uword_hex(0, $1200)
        c64.CHROUT(' ')
        c64scr.print_uword_hex(1, $1200)
        c64.CHROUT(' ')
        c64scr.print_uword_hex(0, $fe98)
        c64.CHROUT(' ')
        c64scr.print_uword_hex(1, $fe98)
        c64.CHROUT('\n')


        ;  print_word_decimal works OK!
        c64scr.print_word_decimal(0)
        c64.CHROUT(' ')
        c64scr.print_word_decimal(12345)
        c64.CHROUT(' ')
        c64scr.print_word_decimal(32555)
        c64.CHROUT(' ')
        c64scr.print_word_decimal(uwrd(-1))   ; @todo allow signed values for registerpair
        c64.CHROUT(' ')
        c64scr.print_word_decimal(uwrd(-9999))   ; @todo allow signed values for registerpair
        c64.CHROUT(' ')
        c64scr.print_word_decimal(uwrd(-$5fff))   ; @todo allow signed values for registerpair
        c64.CHROUT(' ')
        c64scr.print_word_decimal(uwrd(-$6000))   ; @todo allow signed values for registerpair
        c64.CHROUT(' ')
        c64scr.print_word_decimal(uwrd(-$6001))   ; @todo allow signed values for registerpair
        c64.CHROUT('\n')


        b1 = -6
        b2 = 30
        c64scr.print_byte_decimal(b2ub(b1))
        c64.CHROUT('+')
        c64scr.print_byte_decimal(b2ub(b2))
        c64.CHROUT('=')
        b1 += b2
        c64scr.print_byte_decimal(b2ub(b1))
        c64.CHROUT('\n')

        b1 = 60
        b2 = -3
        c64scr.print_byte_decimal(b2ub(b1))
        c64.CHROUT('+')
        c64scr.print_byte_decimal(b2ub(b2))
        c64.CHROUT('=')
        b1 += b2
        c64scr.print_byte_decimal(b2ub(b1))
        c64.CHROUT('\n')

        ub1 = 90
        ub2 = 50
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('+')
        c64scr.print_ubyte_decimal(ub2)
        c64.CHROUT('=')
        ub1 += ub2
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')

        ub1 = 50
        ub2 = 90
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('+')
        c64scr.print_ubyte_decimal(ub2)
        c64.CHROUT('=')
        ub1 += ub2
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')


        b1 = -6
        b2 = 30
        c64scr.print_byte_decimal(b2ub(b1))
        c64.CHROUT('-')
        c64scr.print_byte_decimal(b2ub(b2))
        c64.CHROUT('=')
        b1 -= b2
        c64scr.print_byte_decimal(b2ub(b1))
        c64.CHROUT('\n')

        b1 = 60
        b2 = -3
        c64scr.print_byte_decimal(b2ub(b1))
        c64.CHROUT('-')
        c64scr.print_byte_decimal(b2ub(b2))
        c64.CHROUT('=')
        b1 -= b2
        c64scr.print_byte_decimal(b2ub(b1))
        c64.CHROUT('\n')

        ub1 = 90
        ub2 = 50
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('-')
        c64scr.print_ubyte_decimal(ub2)
        c64.CHROUT('=')
        ub1 -= ub2
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')

        ub1 = 50
        ub2 = 90
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('-')
        c64scr.print_ubyte_decimal(ub2)
        c64.CHROUT('=')
        ub1 -= ub2
        c64scr.print_ubyte_decimal(ub1)
        c64.CHROUT('\n')

        w1 = -600
        w2 = 30000
        c64scr.print_uword_hex(1, uwrd(w1))
        c64.CHROUT('+')
        c64scr.print_uword_hex(1, uwrd(w2))
        c64.CHROUT('=')
        w1 += w2
        c64scr.print_uword_hex(1, uwrd(w1))
        c64.CHROUT('\n')

        w1 = 600
        w2 = -30000
        c64scr.print_uword_hex(1, uwrd(w1))
        c64.CHROUT('+')
        c64scr.print_uword_hex(1, uwrd(w2))
        c64.CHROUT('=')
        w1 += w2
        c64scr.print_uword_hex(1, uwrd(w1))
        c64.CHROUT('\n')

        uw1 = 600
        uw2 = 40000
        c64scr.print_uword_decimal(uw1)
        c64.CHROUT('+')
        c64scr.print_uword_decimal(uw2)
        c64.CHROUT('=')
        uw1 += uw2
        c64scr.print_uword_decimal(uw1)
        c64.CHROUT('\n')

        uw1 = 40000
        uw2 = 600
        c64scr.print_uword_decimal(uw1)
        c64.CHROUT('+')
        c64scr.print_uword_decimal(uw2)
        c64.CHROUT('=')
        uw1 += uw2
        c64scr.print_uword_decimal(uw1)
        c64.CHROUT('\n')

    }
}

