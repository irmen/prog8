%import gfx2
%import textio
%import math

%option no_sysinit
%zeropage basicsafe


main {

    sub start() {
        gfx2.screen_mode(4)
        demofill()
        sys.wait(2*60)
        gfx2.screen_mode(5)
        demo1()
        sys.wait(2*60)
        demo2()

        gfx2.screen_mode(0)
        txt.print("done!\n")
    }

    sub demofill() {

        gfx2.circle(160, 120, 110, 1)
        gfx2.rect(180, 5, 25, 190, 1)
        gfx2.line(100, 150, 240, 10, 1)
        gfx2.line(101, 150, 241, 10, 1)
        ;gfx2.monochrome_stipple(true)
        sys.wait(60)
        gfx2.fill(100,100,2)
        ;gfx2.monochrome_stipple(false)
        gfx2.fill(182,140,3)
        gfx2.fill(182,40,1)

    }

    sub demo1() {
        uword yy = 10
        uword xx
        uword cnt

        gfx2.monochrome_stipple(true)
        gfx2.disc(320,240,200,1)
        for xx in 0 to 639 {
            gfx2.vertical_line(xx, 0, 480, 1)
        }
        for xx in 0 to 639 {
            gfx2.vertical_line(xx, 0, 480, 0)
        }

        xx=gfx2.width/2
        yy=10
        gfx2.monochrome_stipple(false)
        linesy()
        linesx()
        gfx2.monochrome_stipple(true)
        linesy()
        linesx()



        sub linesx() {
            repeat 8 {
                gfx2.horizontal_line(10,yy,300,3)
                yy++
            }
            yy+=4

            repeat 8 {
                gfx2.line(10,yy,309,yy,4)
                yy++
            }
            yy+=4

            repeat 8 {
                for cnt in 10 to 309 {
                    gfx2.plot(cnt, yy, 1)
                }
                yy+=1
            }
            yy += 4

            repeat 8 {
                gfx2.horizontal_line(10,yy,100,3)
                yy++
            }
            yy+=4

            repeat 8 {
                gfx2.line(10,yy,109,yy,4)
                yy++
            }
            yy+=4

            repeat 8 {
                for cnt in 10 to 109 {
                    gfx2.plot(cnt, yy, 1)
                }
                yy++
            }
            yy+=4
        }

        sub linesy() {
            repeat 8 {
                gfx2.vertical_line(xx,10,300,3)
                xx++
            }
            xx+=4

            repeat 8 {
                gfx2.line(xx,10, xx, 309, 4)
                xx++
            }
            xx+=4

            repeat 8 {
                for cnt in 10 to 309 {
                    gfx2.plot(xx, cnt, 1)
                }
                xx+=1
            }
            xx += 4

            repeat 8 {
                gfx2.vertical_line(xx,10,100,3)
                xx++
            }
            xx+=4

            repeat 8 {
                gfx2.line(xx,10,xx,109,4)
                xx++
            }
            xx+=4

            repeat 8 {
                for cnt in 10 to 109 {
                    gfx2.plot(xx, cnt, 1)
                }
                xx++
            }
            xx+=4
        }
    }

    sub demo2 () {
        gfx2.text_charset(3)

        ubyte[] modes = [4, 1, 5]
        ubyte mode
        for mode in modes {
            gfx2.screen_mode(mode)
            draw()
            sys.wait(200)
        }

    }

    sub draw() {

        gfx2.rect(10,10, 1, 1, 4)
        gfx2.rect(20,10, 2, 1, 4)
        gfx2.rect(30,10, 3, 1, 4)
        gfx2.rect(40,10, 1, 2, 4)
        gfx2.rect(50,10, 1, 3, 4)
        gfx2.rect(60,10, 2, 2, 4)
        gfx2.rect(70,10, 3, 3, 4)
        gfx2.rect(80,10, 4, 4, 4)
        gfx2.rect(90,10, 5, 5, 4)
        gfx2.rect(100,10, 8, 8, 4)
        gfx2.rect(110,10, 20, 5, 4)
        gfx2.rect(80, 80, 200, 140, 4)

        gfx2.fillrect(10,40, 1, 1, 5)
        gfx2.fillrect(20,40, 2, 1, 5)
        gfx2.fillrect(30,40, 3, 1, 5)
        gfx2.fillrect(40,40, 1, 2, 5)
        gfx2.fillrect(50,40, 1, 3, 5)
        gfx2.fillrect(60,40, 2, 2, 5)
        gfx2.fillrect(70,40, 3, 3, 5)
        gfx2.fillrect(80,40, 4, 4, 5)
        gfx2.fillrect(90,40, 5, 5, 5)
        gfx2.fillrect(100,40, 8, 8, 5)
        gfx2.fillrect(110,40, 20, 5, 5)
        gfx2.fillrect(82, 82, 200-4, 140-4, 5)

        ubyte i
        for i in 0 to 254 step 4 {
            uword x1 = ((gfx2.width-256)/2 as uword) + math.sin8u(i)
            uword y1 = (gfx2.height-128)/2 + math.cos8u(i)/2
            uword x2 = ((gfx2.width-64)/2 as uword) + math.sin8u(i)/4
            uword y2 = (gfx2.height-64)/2 + math.cos8u(i)/4
            gfx2.line(x1, y1, x2, y2, i+1)
        }

        sys.wait(60)
        gfx2.clear_screen()

        ubyte radius

        for radius in 110 downto 8 step -4 {
            gfx2.circle(gfx2.width/2, (gfx2.height/2 as ubyte), radius, radius)
        }

        gfx2.disc(gfx2.width/2, gfx2.height/2, 80, 2)

        ubyte tp
        for tp in 0 to 15 {
            gfx2.text(19+tp,20+tp*11, 7, sc:"ScreenCODE text! 1234![]<>#$%&*()")
        }

    }
}
