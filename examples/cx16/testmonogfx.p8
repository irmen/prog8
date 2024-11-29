%import monogfx
%import textio
%import math

%option no_sysinit
%zeropage basicsafe


main {
    const ubyte FILL_STACK_BANK = 2

    sub start() {
        monogfx.lores()
        demofill()
        sys.wait(2*60)
        monogfx.hires()
        demo1()
        sys.wait(2*60)
        demo2()

        monogfx.textmode()
        txt.print("done!\n")
    }

    sub demofill() {
        monogfx.circle(160, 120, 110, true)
        monogfx.rect(180, 5, 25, 190, true)
        monogfx.line(100, 150, 240, 10, true)
        monogfx.line(101, 150, 241, 10, true)
        monogfx.drawmode(monogfx.MODE_STIPPLE)
        sys.wait(60)
        monogfx.fill(100,100,true, FILL_STACK_BANK)
    }

    sub demo1() {
        uword yy = 10
        uword xx
        uword cnt

        monogfx.drawmode(monogfx.MODE_STIPPLE)
        monogfx.disc(320,240,200,true)
        for xx in 0 to 639 {
            monogfx.vertical_line(xx, 0, 480, true)
        }
        for xx in 0 to 639 {
            monogfx.vertical_line(xx, 0, 480, false)
        }

        xx=monogfx.width/2
        yy=10
        monogfx.drawmode(monogfx.MODE_NORMAL)
        linesy()
        linesx()
        monogfx.drawmode(monogfx.MODE_STIPPLE)
        linesy()
        linesx()



        sub linesx() {
            repeat 8 {
                monogfx.horizontal_line(10,yy,300,true)
                yy++
            }
            yy+=4

            repeat 8 {
                monogfx.line(10,yy,309,yy,false)
                yy++
            }
            yy+=4

            repeat 8 {
                for cnt in 10 to 309 {
                    monogfx.plot(cnt, yy, true)
                }
                yy+=1
            }
            yy += 4

            repeat 8 {
                monogfx.horizontal_line(10,yy,100,true)
                yy++
            }
            yy+=4

            repeat 8 {
                monogfx.line(10,yy,109,yy,false)
                yy++
            }
            yy+=4

            repeat 8 {
                for cnt in 10 to 109 {
                    monogfx.plot(cnt, yy, true)
                }
                yy++
            }
            yy+=4
        }

        sub linesy() {
            repeat 8 {
                monogfx.vertical_line(xx,10,300,true)
                xx++
            }
            xx+=4

            repeat 8 {
                monogfx.line(xx,10, xx, 309, false)
                xx++
            }
            xx+=4

            repeat 8 {
                for cnt in 10 to 309 {
                    monogfx.plot(xx, cnt, true)
                }
                xx+=1
            }
            xx += 4

            repeat 8 {
                monogfx.vertical_line(xx,10,100,true)
                xx++
            }
            xx+=4

            repeat 8 {
                monogfx.line(xx,10,xx,109,false)
                xx++
            }
            xx+=4

            repeat 8 {
                for cnt in 10 to 109 {
                    monogfx.plot(xx, cnt, true)
                }
                xx++
            }
            xx+=4
        }
    }

    sub demo2 () {
        monogfx.text_charset(3)
        monogfx.lores()
        draw()
        sys.wait(100)
        monogfx.hires()
        draw()
        sys.wait(100)
        monogfx.drawmode(monogfx.MODE_INVERT)
        repeat 7 {
            for cx16.r8 in 100 to 400 {
                monogfx.vertical_line(cx16.r8, 0, 400, true)
            }
        }
    }

    sub draw() {

        monogfx.rect(10,10, 1, 1, true)
        monogfx.rect(20,10, 2, 1, true)
        monogfx.rect(30,10, 3, 1, true)
        monogfx.rect(40,10, 1, 2, true)
        monogfx.rect(50,10, 1, 3, true)
        monogfx.rect(60,10, 2, 2, true)
        monogfx.rect(70,10, 3, 3, true)
        monogfx.rect(80,10, 4, 4, true)
        monogfx.rect(90,10, 5, 5, true)
        monogfx.rect(100,10, 8, 8, true)
        monogfx.rect(110,10, 20, 5, true)
        monogfx.rect(80, 80, 200, 140, true)

        monogfx.fillrect(10,40, 1, 1, true)
        monogfx.fillrect(20,40, 2, 1, true)
        monogfx.fillrect(30,40, 3, 1, true)
        monogfx.fillrect(40,40, 1, 2, true)
        monogfx.fillrect(50,40, 1, 3, true)
        monogfx.fillrect(60,40, 2, 2, true)
        monogfx.fillrect(70,40, 3, 3, true)
        monogfx.fillrect(80,40, 4, 4, true)
        monogfx.fillrect(90,40, 5, 5, true)
        monogfx.fillrect(100,40, 8, 8, true)
        monogfx.fillrect(110,40, 20, 5, true)
        monogfx.fillrect(82, 82, 200-4, 140-4, true)

        ubyte i
        for i in 0 to 254 step 4 {
            uword x1 = ((monogfx.width-256)/2 as uword) + math.sin8u(i)
            uword y1 = (monogfx.height-128)/2 + math.cos8u(i)/2
            uword x2 = ((monogfx.width-64)/2 as uword) + math.sin8u(i)/4
            uword y2 = (monogfx.height-64)/2 + math.cos8u(i)/4
            monogfx.line(x1, y1, x2, y2, true)
        }

        sys.wait(60)
        monogfx.clear_screen(true)
        monogfx.clear_screen(false)

        ubyte radius

        for radius in 110 downto 8 step -4 {
            monogfx.circle(monogfx.width/2, (monogfx.height/2 as ubyte), radius, true)
        }

        monogfx.disc(monogfx.width/2, monogfx.height/2, 80, true)

        ubyte tp
        for tp in 0 to 15 {
            monogfx.text(19+tp, 20+tp*11, true, sc:"ScreenCODE text! 1234![]<>#$%&*()")
        }

    }
}
