%import gfx_lores
%import textio
%import math

%option no_sysinit
%zeropage basicsafe


main {

    const ubyte FILL_STACK_BANK = 2

    sub start() {
        demofill()
        sys.wait(120)
        demo2()

        gfx_lores.text_mode()
        txt.print("done!\n")
    }

    sub demofill() {
        gfx_lores.graphics_mode()
        gfx_lores.circle(160, 120, 110, 1)
        gfx_lores.rect(180, 5, 25, 190, 1)
        gfx_lores.line(100, 150, 240, 10, 1)
        gfx_lores.line(101, 150, 241, 10, 1)
        sys.wait(60)
        gfx_lores.fill(100,100,2, FILL_STACK_BANK)
        gfx_lores.fill(182,140,3, FILL_STACK_BANK)
        gfx_lores.fill(182,40,1, FILL_STACK_BANK)

    }

    sub demo2 () {
        gfx_lores.text_charset(3)
        draw()
        sys.wait(120)
    }

    sub draw() {
        gfx_lores.rect(10,10, 1, 1, 4)
        gfx_lores.rect(20,10, 2, 1, 4)
        gfx_lores.rect(30,10, 3, 1, 4)
        gfx_lores.rect(40,10, 1, 2, 4)
        gfx_lores.rect(50,10, 1, 3, 4)
        gfx_lores.rect(60,10, 2, 2, 4)
        gfx_lores.rect(70,10, 3, 3, 4)
        gfx_lores.rect(80,10, 4, 4, 4)
        gfx_lores.rect(90,10, 5, 5, 4)
        gfx_lores.rect(100,10, 8, 8, 4)
        gfx_lores.rect(110,10, 20, 5, 4)
        gfx_lores.rect(80, 80, 200, 140, 4)

        gfx_lores.fillrect(10,40, 1, 1, 5)
        gfx_lores.fillrect(20,40, 2, 1, 5)
        gfx_lores.fillrect(30,40, 3, 1, 5)
        gfx_lores.fillrect(40,40, 1, 2, 5)
        gfx_lores.fillrect(50,40, 1, 3, 5)
        gfx_lores.fillrect(60,40, 2, 2, 5)
        gfx_lores.fillrect(70,40, 3, 3, 5)
        gfx_lores.fillrect(80,40, 4, 4, 5)
        gfx_lores.fillrect(90,40, 5, 5, 5)
        gfx_lores.fillrect(100,40, 8, 8, 5)
        gfx_lores.fillrect(110,40, 20, 5, 5)
        gfx_lores.fillrect(82, 82, 200-4, 140-4, 5)

        ubyte i
        for i in 0 to 254 step 4 {
            uword x1 = ((gfx_lores.WIDTH-256)/2 as uword) + math.sin8u(i)
            ubyte y1 = (gfx_lores.HEIGHT-128)/2 + math.cos8u(i)/2
            uword x2 = ((gfx_lores.WIDTH-64)/2 as uword) + math.sin8u(i)/4
            ubyte y2 = (gfx_lores.HEIGHT-64)/2 + math.cos8u(i)/4
            gfx_lores.line(x1, y1, x2, y2, i+1)
        }

        sys.wait(60)
        gfx_lores.clear_screen(2)
        gfx_lores.clear_screen(0)

        ubyte radius

        for radius in 110 downto 8 step -4 {
            gfx_lores.circle(gfx_lores.WIDTH/2, (gfx_lores.HEIGHT/2 as ubyte), radius, radius)
        }

        gfx_lores.disc(gfx_lores.WIDTH/2, gfx_lores.HEIGHT/2, 80, 2)

        ubyte tp
        for tp in 0 to 15 {
            gfx_lores.text(19+tp,20+tp*11, 7, sc:"ScreenCODE text! 1234![]<>#$%&*()")
        }

    }
}
