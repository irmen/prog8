
~ main {

sub start() -> () {

    byte i

    byte from = 0
    byte last = 5

    word iw

    word fromw = 0
    word lastw = 5

    for i in 10 to 1 step -1 {
        _vm_write_num(i)
        _vm_write_char($8d)
    }
    _vm_write_char($8d)
    _vm_write_char($8d)

    for i in 10 to 0  step -1 {
        _vm_write_num(i)
        _vm_write_char($8d)
    }
    _vm_write_char($8d)
    _vm_write_char($8d)

    for i in from to last  {
        _vm_write_num(i)
        _vm_write_char($8d)
    }
    _vm_write_char($8d)
    _vm_write_char($8d)

    from=250
    last=255
    for i in from to last  {
        _vm_write_num(i)
        _vm_write_char($8d)
    }
    _vm_write_char($8d)
    _vm_write_char($8d)

    fromw=65530
    lastw=65535
    for iw in fromw to lastw  {
        _vm_write_num(iw)
        _vm_write_char($8d)
    }
    _vm_write_char($8d)
    _vm_write_char($8d)


    from = 5
    last = 1

    fromw = 1
    lastw = 5

    for iw in fromw to lastw step 1 {     ;@todo last 0
        _vm_write_num(iw)
        _vm_write_char($8d)
    }
    _vm_write_char($8d)
    _vm_write_char($8d)
}
}
