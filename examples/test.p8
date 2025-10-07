%import textio
%zeropage basicsafe

main {
    sub start() {
        long @shared lv1 = -9999
;        txt.print_uw(lsw(lv1))
;        txt.spc()
;        txt.print_w(lv1 as word)
;        txt.spc()
;        txt.print_uw(lv1 as uword)
;        txt.spc()
;        txt.print_b(lv1 as byte)
;        txt.spc()
;        txt.print_ub(lv1 as ubyte)
;        txt.spc()
;        txt.print_w(msw(lv1 << 8) as word)
;        txt.spc()
;        txt.print_w(lsw(lv1 >> 8) as word)

        txt.nl()
        txt.nl()
        lv1 = -9876543
        conv.str_ub0(123)
        txt.print(conv.str_l(lv1))
        txt.spc()
        txt.print(conv.string_out)
        txt.nl()
        lv1 = 123456
        txt.print(conv.str_l(lv1))
        txt.spc()
        txt.print(conv.string_out)
        txt.nl()
        lv1 = -2147483647
        txt.print(conv.str_l(lv1))
        txt.spc()
        txt.print(conv.string_out)
        txt.nl()
        lv1 = 2147483647
        txt.print(conv.str_l(lv1))
        txt.spc()
        txt.print(conv.string_out)
        txt.nl()

    }
}
