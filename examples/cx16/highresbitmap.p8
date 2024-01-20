%import monogfx
%import floats
%import textio
%import math
%zeropage dontuse

main {

    sub start () {
        monogfx.text_charset(3)

        test_monochrome()

        monogfx.textmode()
        txt.print("done!\n")
    }

    sub test_monochrome() {
        monogfx.hires()
        uword yy
        uword xx
        word ww

        yy = 20
        xx = 20
        monogfx.stipple(false)
        monogfx.rect(xx, yy, 250, 80, true)
        monogfx.stipple(true)
        monogfx.fillrect(xx+2, yy+2, 250-4, 80-4, true)
        monogfx.stipple(false)
        monogfx.fillrect(xx+20, yy+20, 200, 30, true)
        monogfx.rect(xx+21, yy+21, 200-2, 30-2, false)

        monogfx.text(xx+30, yy+32, false, sc:"High Res Bitmap Example")

        ; monogfx.stipple(true)
        monogfx.horizontal_line(10, 240, 620, true)
        monogfx.vertical_line(320, 10, 460, true)
        monogfx.text(320, 242, true, sc:"0,0")
        monogfx.text(322, 10, true, sc:"Y-axis")
        monogfx.text(590, 242, true, sc:"X-axis")
        for ww in -10 to 10 {
            xx = (ww*30) + 320 as uword
            monogfx.vertical_line(xx, 239, 3, true)
        }
        for ww in -7 to 7 {
            yy = (ww*30) + 240 as uword
            monogfx.horizontal_line(319, yy, 3, true)
        }

        monogfx.stipple(false)
        float y_f
        for ww in -600 to 600 {
            y_f = floats.sin(ww as float / 60.0)*150
            monogfx.plot(ww/2 + 320 as uword, (y_f + 240) as uword, true)
        }
        monogfx.text(480, 100, true, sc:"sin(x)")

        for ww in -300 to 300 {
            y_f = floats.cos(ww as float/30.0)*60 - (ww as float)/1.7
            monogfx.plot(ww + 320 as uword, (y_f + 240) as uword, true)
        }
        monogfx.text(80, 420, true, sc:"cos(x)+x")

        sys.wait(3*60)

        monogfx.circle(320, 240, 220, true)
        monogfx.circle(320, 240, 210, true)
        monogfx.circle(320, 240, 200, true)
        monogfx.circle(320, 240, 190, true)
        monogfx.stipple(true)
        monogfx.disc(320, 240, 140, true)
        monogfx.stipple(false)
        monogfx.disc(320, 240, 90, true)
        monogfx.disc(320, 240, 40, false)

        sys.wait(2*60)

        repeat 255
            monogfx.line(math.rndw() % 640, math.rndw() % 480, math.rndw() % 640, math.rndw() % 480, true)

        sys.wait(1*60)
    }
}
