%option enable_floats



~ main {

sub start() {

    repeat {
        _vm_write_str("333\n")
    } until(1)

    repeat {
        _vm_write_str("444\n")
    } until (0)

    return

}


}
