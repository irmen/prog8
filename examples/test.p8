%import graphics
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        graphics.enable_bitmap_mode()
        for cx16.r11L in 0 to 110 {
            graphics.horizontal_line(cx16.r11L+10, cx16.r11L+20, cx16.r11L+5)
        }
    }
}
