%import gfx2
%import floats
%import textio
%zeropage dontuse

main {

    sub start () {
        gfx2.text_charset(3)

        test_monochrome()

        gfx2.screen_mode(0)
        txt.print("done!\n")
    }

    sub test_monochrome() {
        gfx2.screen_mode(5)
        uword yy
        uword xx
        word ww

        yy = 20
        xx = 20
        gfx2.monochrome_stipple(false)
        gfx2.rect(xx, yy, 250, 80, 1)
        gfx2.monochrome_stipple(true)
        gfx2.fillrect(xx+2, yy+2, 250-4, 80-4, 1)
        gfx2.monochrome_stipple(false)
        gfx2.fillrect(xx+20, yy+20, 200, 30, 1)
        gfx2.rect(xx+21, yy+21, 200-2, 30-2, 0)

        gfx2.text(xx+30, yy+32, 0, @"High Res Bitmap Example")

        ; gfx2.monochrome_stipple(true)
        gfx2.horizontal_line(10, 240, 620, 1)
        gfx2.vertical_line(320, 10, 460, 1)
        gfx2.text(320, 242, 1, @"0,0")
        gfx2.text(322, 10, 1, @"Y-axis")
        gfx2.text(590, 242, 1, @"X-axis")
        for ww in -10 to 10 {
            xx = (ww*30) + 320 as uword
            gfx2.vertical_line(xx, 239, 3, 1)
        }
        for ww in -7 to 7 {
            yy = (ww*30) + 240 as uword
            gfx2.horizontal_line(319, yy, 3, 1)
        }

        gfx2.monochrome_stipple(false)
        float y_f
        for ww in -600 to 600 {
            y_f = sin(ww as float / 60.0)*150
            gfx2.plot(ww/2 + 320 as uword, (y_f + 240) as uword, 1)
        }
        gfx2.text(480, 100, 1, @"sin(x)")

        for ww in -300 to 300 {
            y_f = cos(ww as float/30.0)*60 - (ww as float)/1.7
            gfx2.plot(ww + 320 as uword, (y_f + 240) as uword, 1)
        }
        gfx2.text(80, 420, 1, @"cos(x)+x")

        sys.wait(3*60)

        gfx2.circle(320, 240, 220, 1)
        gfx2.circle(320, 240, 210, 1)
        gfx2.circle(320, 240, 200, 1)
        gfx2.circle(320, 240, 190, 1)
        gfx2.monochrome_stipple(true)
        gfx2.disc(320, 240, 140, 1)
        gfx2.monochrome_stipple(false)
        gfx2.disc(320, 240, 90, 1)
        gfx2.disc(320, 240, 40, 0)

        sys.wait(2*60)

        repeat 255
            gfx2.line(rndw() % 640, rndw() % 480, rndw() % 640, rndw() % 480, 1)

        sys.wait(1*60)
    }
}
