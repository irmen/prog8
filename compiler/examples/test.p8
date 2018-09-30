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
    float eol = '\n'


    _vm_write_char('\n')
    fvar=test(15, 222.22)
    _vm_write_num(fvar)
    _vm_write_char('\n')
    fvar=test(17, 333.33)
    _vm_write_num(fvar)
    _vm_write_char('\n')
    return

sub test(arg: byte, f: float) -> float {
    return f/arg
}

}
}
