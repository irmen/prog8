
~ main {

sub start() -> () {

    byte i=2

    while i<10 {
        _vm_write_num(i)
        _vm_write_char($8d)
        i++
    }

    _vm_write_char($8d)

    i=2
    repeat {
        _vm_write_num(i)
        _vm_write_char($8d)
        i++
    } until i>10

}
}
