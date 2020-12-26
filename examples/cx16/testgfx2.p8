%target cx16
%import gfx2
%import textio
%zeropage basicsafe

main {

    sub start () {
        gfx2.text_charset(3)

        ubyte[] modes = [0, 1, 128]
        ubyte mode
        for mode in modes {
            gfx2.screen_mode(mode)
            draw()
            ubyte tp
            for tp in 0 to 15 {
                gfx2.text(19+tp,20+tp*11, 5, @"ScreenCODE text! 1234![]<>#$%&*()")
            }
            cx16.wait(120)
        }
        gfx2.screen_mode(255)
        txt.print("done!\n")
    }

    sub draw() {
        uword offset
        ubyte angle
        uword x
        uword y
        when gfx2.active_mode {
            0, 1 -> {
                for offset in 0 to 90 step 3 {
                    for angle in 0 to 255 {
                        x = $0008+sin8u(angle)/2
                        y = $0008+cos8u(angle)/2
                        gfx2.plot(x+offset*2,y+offset, lsb(x+y))
                    }
                }
            }
            128 -> {
                for offset in 0 to 190 step 6 {
                    for angle in 0 to 255 {
                        x = $0008+sin8u(angle)
                        y = $0008+cos8u(angle)
                        gfx2.plot(x+offset*2,y+offset, 1)
                    }
                }
            }
        }
    }
}
