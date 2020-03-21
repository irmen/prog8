%import c64utils
;%import c64flt
;%option enable_floats
%zeropage basicsafe

main {

    sub start() {
        c64scr.clear_screen('*',7)
        c64.CHRIN()
        c64scr.clear_screen('.',2)

    }
}
