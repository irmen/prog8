%option enable_floats



~ main {

sub start() {

    str s1 = "hello"
    str s2 = "bye"

    A=X
    X=Y
    X=X

    _vm_write_str(s1)
    s1 = s2
    _vm_write_str(s1)
    s1 = "ciao"
    _vm_write_str(s1)

    return

}


}
