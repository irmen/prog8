%option enable_floats



~ main {

sub start() {

    const byte b1 = 20//7
    const word w1 = 20//7
    const float f1 = 20/7

    _vm_write_num(b1)
    _vm_write_char('\n')
    _vm_write_num(w1)
    _vm_write_char('\n')
    _vm_write_num(f1)
    _vm_write_char('\n')
    return

}

}
