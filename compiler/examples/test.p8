
~ main {

sub start() -> () {

    byte i

    byte from = 250
    byte last = 255

    for i in from to last  {
        _vm_write_num(i)
        _vm_write_char($8d)
    }

;    _vm_write_char($8d)
;    _vm_write_num(i)
;    _vm_write_char($8d)
;    _vm_write_char($8d)
;
;    from = 8
;    last = 0
;
;    for i in from to last step -1 {
;        _vm_write_num(i)
;        _vm_write_char($8d)
;    }
;
;    _vm_write_char($8d)
;    _vm_write_num(i)
;    _vm_write_char($8d)

}
}
