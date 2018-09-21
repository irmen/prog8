
~ main {

sub start() -> () {

    byte i

    byte from = 0
    byte last = 5

    if (i>last) {
        _vm_write_num(100)
        _vm_write_char($8d)
    }


;    for i in from to last  {
;        _vm_write_num(i)
;        _vm_write_char($8d)
;    }
;    _vm_write_char($8d)
;    _vm_write_char($8d)

;    from=250
;    last=255
;    for i in from to last  {
;        _vm_write_num(i)
;        _vm_write_char($8d)
;    }
;    _vm_write_char($8d)
;    _vm_write_char($8d)
;
;
;    from = 8
;    last = 0
;
;    for i in from to last step -1 {
;        _vm_write_num(i)
;        _vm_write_char($8d)
;    }
;    _vm_write_char($8d)
;    _vm_write_char($8d)

}
}
