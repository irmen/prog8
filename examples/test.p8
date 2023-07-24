%import gfx2


main {
    sub start() {
        gfx2.screen_mode(1)         ; 1 and 5 are lo-res and hi-res monochrome

        uword xx
        gfx2.rect(10, 10, 180, 140, 3)
        gfx2.rect(12, 12, 180, 140, 3)
        for xx in 5 to 100 {
            gfx2.text(xx, xx, 1, sc:"hello world! should be pixel-aligned.")
            sys.waitvsync()
            sys.waitvsync()
            gfx2.text(xx, xx, 0, sc:"hello world! should be pixel-aligned.")
        }

        repeat { }
	}
}
