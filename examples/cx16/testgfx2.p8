%target cx16
%import gfx2
%import textio
%zeropage basicsafe

main {

    sub start () {
        gfx2.text_charset(3)

        ubyte[] modes = [1, 0, 128]
        ubyte mode
        for mode in modes {
            gfx2.screen_mode(mode)
            draw()
            ubyte tp
            for tp in 0 to 15 {
                gfx2.text(19+tp,20+tp*11, 5, @"ScreenCODE text! 1234![]<>#$%&*()")
            }
            cx16.wait(200)
        }

        gfx2.screen_mode(255)
        txt.print("done!\n")
    }

    sub draw() {
        ubyte radius

        for radius in 110 downto 8 step -4 {
            gfx2.circle(gfx2.width/2, (gfx2.height/2 as ubyte), radius, radius)
        }

        gfx2.disc(gfx2.width/2, gfx2.height/2, 108, 255)
    }
}

gfx3 {


}
