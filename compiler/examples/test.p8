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

    A=xyz()
    asmxyz(333, 2)
    asmxyz(333,2)
    X=asmxyz2(333,2)
    return

}


sub xyz() -> byte {

    return 33
}

asmsub asmxyz(v1: word @ XY, v2: byte @ A) -> clobbers() -> (byte @ A, word @ XY) {
    return 44,4455
}
asmsub asmxyz2(v1: word @ XY, v2: byte @ A) -> clobbers(X) -> (byte @ A) {
    return 44
}
}
