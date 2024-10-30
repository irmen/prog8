%import gfx_hires4
%import textio
%import math

%option no_sysinit
%zeropage basicsafe


main {

    sub start() {
        gfx_hires4.graphics_mode()
        draw()
        sys.wait(120)
        gfx_hires4.text_mode()
        txt.print("done!\n")
    }

    sub draw() {
        gfx_hires4.rect(10,10, 1, 1, 4)
        gfx_hires4.rect(20,10, 2, 1, 4)
        gfx_hires4.rect(30,10, 3, 1, 4)
        gfx_hires4.rect(40,10, 1, 2, 4)
        gfx_hires4.rect(50,10, 1, 3, 4)
        gfx_hires4.rect(60,10, 2, 2, 4)
        gfx_hires4.rect(70,10, 3, 3, 4)
        gfx_hires4.rect(80,10, 4, 4, 4)
        gfx_hires4.rect(90,10, 5, 5, 4)
        gfx_hires4.rect(100,10, 8, 8, 4)
        gfx_hires4.rect(110,10, 20, 5, 4)
        gfx_hires4.rect(80, 80, 200, 140, 4)

        gfx_hires4.fillrect(10,40, 1, 1, 5)
        gfx_hires4.fillrect(20,40, 2, 1, 5)
        gfx_hires4.fillrect(30,40, 3, 1, 5)
        gfx_hires4.fillrect(40,40, 1, 2, 5)
        gfx_hires4.fillrect(50,40, 1, 3, 5)
        gfx_hires4.fillrect(60,40, 2, 2, 5)
        gfx_hires4.fillrect(70,40, 3, 3, 5)
        gfx_hires4.fillrect(80,40, 4, 4, 5)
        gfx_hires4.fillrect(90,40, 5, 5, 5)
        gfx_hires4.fillrect(100,40, 8, 8, 5)
        gfx_hires4.fillrect(110,40, 20, 5, 5)
        gfx_hires4.fillrect(82, 82, 200-4, 140-4, 5)

        ubyte i
        for i in 0 to 254 step 4 {
            uword x1 = ((gfx_hires4.WIDTH-256)/2 as uword) + math.sin8u(i)
            uword y1 = (gfx_hires4.HEIGHT-128)/2 + math.cos8u(i)/2
            uword x2 = ((gfx_hires4.WIDTH-64)/2 as uword) + math.sin8u(i)/4
            uword y2 = (gfx_hires4.HEIGHT-64)/2 + math.cos8u(i)/4
            gfx_hires4.line(x1, y1, x2, y2, i+1)
        }

        sys.wait(60)
        gfx_hires4.clear_screen(2)
        gfx_hires4.clear_screen(0)

        ubyte radius

        for radius in 110 downto 8 step -4 {
            gfx_hires4.circle(gfx_hires4.WIDTH/2, (gfx_hires4.HEIGHT/2 as ubyte), radius, radius)
        }

        gfx_hires4.disc(gfx_hires4.WIDTH/2, gfx_hires4.HEIGHT/2, 80, 2)

        ubyte tp
        for tp in 0 to 15 {
            gfx_hires4.text(19+tp,20+tp*11, 7, sc:"ScreenCODE text! 1234![]<>#$%&*()")
        }

    }
}
