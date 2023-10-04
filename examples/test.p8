%import monogfx

main {

    sub start() {
        monogfx.lores()
        draw()
        repeat {
        }
    }

    sub draw() {
        uword xx
        monogfx.stipple(true)
        monogfx.fillrect(0,0,200,200,true)
        monogfx.stipple(false)
        for xx in 0 to 255  {
            monogfx.rect(xx, xx/2, 6, 16, 1)
            sys.waitvsync()
            monogfx.rect(xx, xx/2, 6, 16, 0)
        }

        monogfx.rect(100, 100, 16, 16, 1)
        monogfx.rect(101, 101, 15, 15, 1)
        monogfx.rect(102, 102, 14, 14, 1)
        monogfx.rect(103, 103, 13, 13, 1)
        monogfx.rect(104, 104, 12, 12, 1)

;        monogfx.rect(10,10, 1, 1,   1)
;        monogfx.rect(20,10, 2, 1,   1)
;        monogfx.rect(30,10, 3, 1,   1)
;        monogfx.rect(40,10, 1, 2,   1)
;        monogfx.rect(50,10, 1, 3,   1)
;        monogfx.rect(60,10, 2, 2,   1)
;        monogfx.rect(70,10, 3, 3,   1)
;        monogfx.rect(80,10, 4, 4,   1)
;        monogfx.rect(120,220, 5, 5,   1)
;        monogfx.rect(90,10, 5, 5,   1)
;        monogfx.rect(100,10, 8, 8,   1)
;        monogfx.rect(110,10, 20, 5,   1)
;        monogfx.fillrect(10,40, 1, 1,   1)
;        monogfx.fillrect(20,40, 2, 1,   1)
;        monogfx.fillrect(30,40, 3, 1,   1)
;        monogfx.fillrect(40,40, 1, 2,   1)
;        monogfx.fillrect(50,40, 1, 3,   1)
;        monogfx.fillrect(60,40, 2, 2,   1)
;        monogfx.fillrect(70,40, 3, 3,   1)
;        monogfx.fillrect(80,40, 4, 4,   1)
;        monogfx.fillrect(90,40, 5, 5,   1)
;        monogfx.fillrect(100,40, 8, 8,   1)
;        monogfx.fillrect(110,40, 20, 5,   1)

;        monogfx.rect(160, 10, 1, 1,   1)
;        monogfx.rect(160, 20, 2, 1,   1)
;        monogfx.rect(160, 30, 3, 1,   1)
;        monogfx.rect(160, 40, 1, 2,   1)
;        monogfx.rect(160, 50, 1, 3,   1)
;        monogfx.rect(160, 60, 2, 2,   1)
;        monogfx.rect(160, 70, 3, 3,   1)
;        monogfx.rect(160, 80, 4, 4,   1)
;        monogfx.rect(160, 90, 5, 5,   1)
;        monogfx.rect(160, 100, 8, 8,   1)
;        monogfx.rect(160, 110, 20, 5,   1)
;        monogfx.fillrect(160, 120, 1, 1,   1)
;        monogfx.fillrect(160, 130, 2, 1,   1)
;        monogfx.fillrect(160, 140, 3, 1,   1)
;        monogfx.fillrect(160, 150, 1, 2,   1)
;        monogfx.fillrect(160, 160, 1, 3,   1)
;        monogfx.fillrect(160, 170, 2, 2,   1)
;        monogfx.fillrect(160, 180, 3, 3,   1)
;        monogfx.fillrect(160, 190, 4, 4,   1)
;        monogfx.fillrect(160, 200, 5, 5,   1)
;        monogfx.fillrect(160, 210, 8, 8,   1)
;        monogfx.fillrect(160, 220, 20, 5,   1)
    }
}
