~ main {

sub start() -> () {

    byte jiffyclockHi = $a0
    byte jiffyclockMid = $a1
    byte jiffyclockLo = $a2

    _vm_gfx_pixel(2,2,jiffyclockHi)
    _vm_gfx_pixel(4,2,jiffyclockMid)
    _vm_gfx_pixel(6,2,jiffyclockLo)
}
}
