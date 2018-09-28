%option enable_floats

~ main {
sub start() -> () {

    A = calcIt(12345, 99)
    ;A = 99/5 + lsb(12345)
    _vm_write_num(A)
    _vm_write_char($8d)
    return

}

sub calcIt(length: XY, control: A) -> (Y) {

    return control/5 +lsb(length)
}
}
