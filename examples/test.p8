%import textio
%import gfx2
%import gfx_lores
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        gfx_lores.graphics_mode()

        ; gfx2.screen_mode(1)
        gfx_lores.text_charset(1)
        for cx16.r9L in 0 to 10 {
            gfx_lores.text(50+cx16.r9L, 20+cx16.r9L, cx16.r9L, sc:"the quick brown fox 12345")
        }
        gfx_lores.text_charset(2)
        for cx16.r9L in 0 to 10 {
            gfx_lores.text(50+cx16.r9L, 40+cx16.r9L, cx16.r9L, sc:"the quick brown fox 12345")
        }
        gfx_lores.text_charset(3)
        for cx16.r9L in 0 to 10 {
            gfx_lores.text(50+cx16.r9L, 60+cx16.r9L, cx16.r9L, sc:"the quick brown fox 12345")
        }
        gfx_lores.text_charset(4)
        for cx16.r9L in 0 to 10 {
            gfx_lores.text(50+cx16.r9L, 80+cx16.r9L, cx16.r9L, sc:"the quick brown fox 12345")
        }
        gfx_lores.text_charset(5)
        for cx16.r9L in 0 to 10 {
            gfx_lores.text(50+cx16.r9L, 100+cx16.r9L, cx16.r9L, sc:"the quick brown fox 12345")
        }

        repeat {
        }
    }
}

