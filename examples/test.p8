%import gfx2
%import textio

main {
    sub start() {
        gfx2.screen_mode(6)         ; 1 and 5 are lo-res and hi-res monochrome

        uword xx
        gfx2.rect(10, 10, 180, 140, 3)
        gfx2.rect(12, 12, 180, 140, 3)

        cbm.SETTIM(0,0,0)

        for xx in 5 to 100 {
            gfx2.text(xx, xx, 1, sc:"hello world! should be pixel-aligned.")
            gfx2.text(xx, xx, 2, sc:"hello world! should be pixel-aligned.")
            gfx2.text(xx, xx, 3, sc:"hello world! should be pixel-aligned.")
            gfx2.text(xx, xx, 0, sc:"hello world! should be pixel-aligned.")
        }

        gfx2.screen_mode(0)
        txt.print_uw(cbm.RDTIM16())
        txt.print(" jiffies")

        repeat { }
	}
}
