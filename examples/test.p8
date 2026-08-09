main {
    str string_out = "????????????"

    ; TODO: bug, this currently crashes the compiler on both m68k targets
    
    sub start() {
        uword ii = 11
        string_out[ii] = 0
        ii--
        string_out[ii] = '0'
    }
}
