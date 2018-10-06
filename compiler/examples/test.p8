%option enable_floats



~ main {

    byte mainb = 44

sub start() {
    test()
    test()
}

sub test() {

    ; @todo test assigning values to array vars
    ; @todo test creating const array vars


    byte subb=42

    for byte i in 0 to 2 {
        float ff=3.3
        byte bb=99
        word ww=1999

        _vm_write_num(ff)
        _vm_write_char('\n')
        _vm_write_num(bb)
        _vm_write_char('\n')
        _vm_write_num(ww)
        _vm_write_char('\n')
        _vm_write_num(subb)
        _vm_write_char('\n')
        _vm_write_num(mainb)
        _vm_write_char('\n')
        _vm_write_char('\n')

        ff += 3
        bb += 3
        ww += 3
        subb += 3
        mainb += 3
    }

    return
}

}
