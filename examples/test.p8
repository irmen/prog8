%import palette
%import textio
%option no_sysinit

main {
    sub start() {
        repeat 4 {
            for cx16.r0L in 0 to 15 {
                txt.color2(cx16.r0L, cx16.r0L)
                txt.spc()
                txt.spc()
                txt.spc()
                txt.spc()
            }
            txt.nl()
        }
        bool changed
        uword[] colors = [
            $f00, $800, $200, $000,
            $f0f, $80f, $20f, $00f
        ]
        do {
            sys.waitvsync()
            sys.waitvsync()
            changed = palette.fade_step_colors(0, 8, colors)
        } until not changed

        sys.wait(60)
        changed = false
        do {
            sys.waitvsync()
            sys.waitvsync()
            changed = palette.fade_step_multi(0, 8, $fff)
        } until not changed
        sys.wait(60)
    }
}
