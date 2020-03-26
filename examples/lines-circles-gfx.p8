%import c64lib
%import c64utils
%zeropage basicsafe


main {

    const uword bitmap_address = $2000


    sub start() {

        ; enable bitmap screen, erase it and set colors to black/white.
        c64.SCROLY |= %00100000
        c64.VMCSB = (c64.VMCSB & %11110000) | %00001000   ; $2000-$3fff
        memset(bitmap_address, 320*200/8, 0)
        c64scr.clear_screen($10, 0)

        circles()
        lines()
    }



    sub circles() {
        ubyte xx
        for xx in 1 to 5 {
            circle(50 + xx*40, 50+xx*15, (xx+10)*4, xx)
            disc(50 + xx*40, 50+xx*15, (xx+10)*2, xx)
        }
    }

    sub lines() {
        uword xx
        ubyte yy
        ubyte color
        ubyte iter

        for color in 1 to 15 {
            xx = color * 16
            yy = color + 20
            for iter in 0 to 80 {
                plot(xx, yy, color)
                xx++
                yy+=2
            }
        }
    }


    sub circle(uword xcenter, ubyte ycenter, ubyte radius, ubyte color) {
        ; Midpoint algorithm
        ubyte x = radius
        ubyte y = 0
        byte decisionOver2 = 1-x

        while x>=y {
            plot(xcenter + x, ycenter + y as ubyte, color)
            plot(xcenter - x, ycenter + y as ubyte, color)
            plot(xcenter + x, ycenter - y as ubyte, color)
            plot(xcenter - x, ycenter - y as ubyte, color)
            plot(xcenter + y, ycenter + x as ubyte, color)
            plot(xcenter - y, ycenter + x as ubyte, color)
            plot(xcenter + y, ycenter - x as ubyte, color)
            plot(xcenter - y, ycenter - x as ubyte, color)
            y++
            if decisionOver2<=0
                decisionOver2 += 2*y+1
            else {
                x--
                decisionOver2 += 2*(y-x)+1
            }
        }
    }

    sub disc(uword cx, ubyte cy, ubyte radius, ubyte color) {
        ; Midpoint algorithm, filled
        ubyte x = radius
        ubyte y = 0
        byte decisionOver2 = 1-x
        uword xx

        while x>=y {
            for xx in cx to cx+x {
                plot(xx, cy + y as ubyte, color)
                plot(xx, cy - y as ubyte, color)
            }
            for xx in cx-x to cx-1 {
                plot(xx, cy + y as ubyte, color)
                plot(xx, cy - y as ubyte, color)
            }
            for xx in cx to cx+y {
                plot(xx, cy + x as ubyte, color)
                plot(xx, cy - x as ubyte, color)
            }
            for xx in cx-y to cx {
                plot(xx, cy + x as ubyte, color)
                plot(xx, cy - x as ubyte, color)
            }
            y++
            if decisionOver2<=0
                decisionOver2 += 2*y+1
            else {
                x--
                decisionOver2 += 2*(y-x)+1
            }
        }
    }

    sub plot(uword px, ubyte py, ubyte color) {
        ; fast asm plot via lookup tables http://codebase64.org/doku.php?id=base:various_techniques_to_calculate_adresses_fast_common_screen_formats_for_pixel_graphics
        ubyte[] ormask = [128, 64, 32, 16, 8, 4, 2, 1]
        uword addr = bitmap_address + 320*(py>>3) + (py & 7) + (px & %0000001111111000)
        @(addr) |= ormask[lsb(px) & 7]
        ubyte sx = px >> 3
        ubyte sy = py >> 3
        c64.SCRATCH_ZPB1 = color << 4
        c64scr.setchr(sx, sy)
    }

}


