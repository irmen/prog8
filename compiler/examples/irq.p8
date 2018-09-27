%option enable_floats

~ main {

    memory byte jiffyclockHi = $a0
    memory byte jiffyclockMid = $a1
    memory byte jiffyclockLo = $a2


sub start() -> () {
    _vm_gfx_pixel(jiffyclockLo,190,jiffyclockHi)
    _vm_gfx_pixel(jiffyclockLo,191,jiffyclockMid)
    _vm_gfx_pixel(jiffyclockLo,192,jiffyclockLo)
    return
}
}
