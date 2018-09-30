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
        ; byte eol = "\n"     ;; todo: convert string of len 1 to byte


    fvar=test(15, 222.22)
    _vm_write_num(fvar)
    _vm_write_char($8d)
    fvar=test(17, 333.33)
    _vm_write_num(fvar)
    _vm_write_char($8d)
    return

sub test(arg: byte, f: float) -> float {
    return f/arg
}

}
}
