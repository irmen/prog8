%option enable_floats

~ main {


sub start() {

    byte bvar = 128
    word wvar = 128
    float fvar = 128

    bvar = 1
    bvar = 2.0
    ;bvar = 2.w     ; @todo don't crash
    wvar = 1        ; @todo optimize byte literal to word literal
    wvar = 2.w
    wvar = 2.0
    wvar = bvar
    fvar = 1        ; @todo optimize byte literal to float literal
    fvar = 2.w      ; @todo optimize word literal to float literal
    fvar = 22.33
    fvar = bvar
    fvar = wvar

    return

}
}
