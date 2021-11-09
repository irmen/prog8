%import graphics

; Note: this program is compatible with CX16 only.
;       it doesn't work correctly on C64 because the bitmap screen data overlaps
;       the program itself in memory $2000-...


main {
    const uword width = 320
    const ubyte height = 200

    sub start()  {
        graphics.enable_bitmap_mode()

        sincos255()
        sys.wait(120)

        graphics.clear_screen(1, 0)

        sincos180()
        sys.wait(120)

        graphics.clear_screen(1, 0)
        circles()

        repeat {
        }
    }

    sub sincos255() {
        graphics.line(256,0,256,height-1)

        ubyte pixelyb
        uword pixelyw
        ubyte pixelxb

        for pixelxb in 0 to 255 {
            pixelyb = cos8u(pixelxb) / 2
            graphics.plot(pixelxb, pixelyb)
            pixelyb = sin8u(pixelxb) / 2
            graphics.plot(pixelxb, pixelyb)
        }

        for pixelxb in 0 to 255 {
            pixelyw = cos16u(pixelxb) / 1024 + 120
            graphics.plot(pixelxb, lsb(pixelyw))
            pixelyw = sin16u(pixelxb) / 1024 + 120
            graphics.plot(pixelxb, lsb(pixelyw))
        }
    }

    sub sincos180() {
        graphics.line(180,0,180,height-1)

        ubyte pixelyb
        uword pixelyw
        ubyte pixelxb

        for pixelxb in 0 to 179 {
            pixelyb = cosr8u(pixelxb) / 2
            graphics.plot(pixelxb, pixelyb)
            pixelyb = sinr8u(pixelxb) / 2
            graphics.plot(pixelxb, pixelyb)
        }

        for pixelxb in 0 to 179 {
            pixelyw = cosr16u(pixelxb) / 1024 + 120
            graphics.plot(pixelxb, lsb(pixelyw))
            pixelyw = sinr16u(pixelxb) / 1024 + 120
            graphics.plot(pixelxb, lsb(pixelyw))
        }
    }

    sub circles() {
        ubyte pixelyb
        uword pixelxw
        ubyte r

        ; circles with "degrees" from 0 to 255
        for r in 0 to 255 {
            pixelxw = (sin8(r)/2 + 80) as uword
            pixelyb = (cos8(r)/2 + height/2) as ubyte
            graphics.plot(pixelxw, pixelyb)
        }

        for r in 0 to 255 {
            pixelxw = (sin16(r)/1024 + 80) as uword
            pixelyb = (cos16(r)/1024 + height/2) as ubyte
            graphics.plot(pixelxw, pixelyb)
        }

        ; circles with half-degrees from 0 to 179 (=full degrees 0..358 with steps of 2 degrees)
        for r in 0 to 179 {
            pixelxw = (sinr8(r) as word /2 + 220) as uword
            pixelyb = (cosr8(r)/2 + height/2) as ubyte
            graphics.plot(pixelxw, pixelyb)
        }

        for r in 0 to 179 {
            pixelxw = (sinr16(r) as word /1024 + 220) as uword
            pixelyb = (cosr16(r)/1024 + height/2) as ubyte
            graphics.plot(pixelxw, pixelyb)
        }

    }
}
