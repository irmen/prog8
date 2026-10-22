main {
    sub start() {
        alias gfx = gfx_hires
        gfx.graphics_mode()
        gfx.somevar = 42
        cx16.r0 = gfx.somevar
        cx16.r1 = gfx.MODE
    }
}
gfx_hires {
    ubyte @shared somevar
    const ubyte MODE = 2
    sub graphics_mode() {
        cx16.r2++
    }
}
