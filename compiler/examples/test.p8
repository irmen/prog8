%option enable_floats

~ main {

sub start() -> () {

    byte i=2
    float f
    word ww = $55aa

    ; P_carry(1)  @todo function -> assignment
    ; P_irqd(1)   @todo function -> assignment
    f=flt(i)
    i = msb(ww)
    i = lsb(ww)
    lsl(i)
    lsr(i)
    rol(i)
    ror(i)
    rol2(i)
    ror2(i)



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
