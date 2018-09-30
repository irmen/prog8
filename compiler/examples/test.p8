%option enable_floats

~ main {
sub start() {
    byte bvar
    word wvar
    float fvar
    str svar = "svar"
    str_p spvar = "spvar"
    str_s ssvar = "ssvar"
    str_ps spsvar = "spsvar"
    byte[2,3] matrixvar
    byte[5] barrayvar
    word[5] warrayvar


    fvar=test(34455)
    return

sub test(arg: byte) -> float {
    return 44.54
}

}
}
