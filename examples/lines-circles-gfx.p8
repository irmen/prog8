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

        lines()
        circles()
    }



    sub circles() {
        ubyte xx
        for xx in 3 to 7 {
            circle(xx*50-100, 10+xx*16, (xx+6)*4)
            disc(xx*50-100, 10+xx*16, (xx+6)*2)
        }
    }

    sub lines() {
        ubyte ix
        for ix in 1 to 15 {
            line(10, 10, ix*4, 50)               ; TODO fix lines of lenghts > 128
        }
    }

    sub line(ubyte x1, ubyte y1, ubyte x2, ubyte y2) {
        ; Bresenham algorithm
        byte d = 0
        ubyte dx = abs(x2 - x1)
        ubyte dy = abs(y2 - y1)
        ubyte dx2 = 2 * dx
        ubyte dy2 = 2 * dy
        byte ix = sgn(x2 as byte - x1 as byte)
        byte iy = sgn(y2 as byte - y1 as byte)
        ubyte x = x1
        ubyte y = y1

        if dx >= dy {
            forever {
                plot(x, y)
                if x==x2
                    return
                x += ix
                d += dy2
                if d > dx {
                    y += iy
                    d -= dx2
                }
            }
        } else {
            forever {
                plot(x, y)
                if y == y2
                    return
                y += iy
                d += dx2
                if d > dy {
                    x += ix
                    d -= dy2
                }
            }
        }
    }

    sub circle(uword xcenter, ubyte ycenter, ubyte radius) {
        ; Midpoint algorithm
        ubyte x = radius
        ubyte y = 0
        byte decisionOver2 = 1-x

        while x>=y {
            plot(xcenter + x, ycenter + y as ubyte)
            plot(xcenter - x, ycenter + y as ubyte)
            plot(xcenter + x, ycenter - y as ubyte)
            plot(xcenter - x, ycenter - y as ubyte)
            plot(xcenter + y, ycenter + x as ubyte)
            plot(xcenter - y, ycenter + x as ubyte)
            plot(xcenter + y, ycenter - x as ubyte)
            plot(xcenter - y, ycenter - x as ubyte)
            y++
            if decisionOver2<=0
                decisionOver2 += 2*y+1
            else {
                x--
                decisionOver2 += 2*(y-x)+1
            }
        }
    }

    sub disc(uword cx, ubyte cy, ubyte radius) {
        ; Midpoint algorithm, filled
        ubyte x = radius
        ubyte y = 0
        byte decisionOver2 = 1-x
        uword xx

        while x>=y {
            for xx in cx to cx+x {
                plot(xx, cy + y as ubyte)
                plot(xx, cy - y as ubyte)
            }
            for xx in cx-x to cx-1 {
                plot(xx, cy + y as ubyte)
                plot(xx, cy - y as ubyte)
            }
            for xx in cx to cx+y {
                plot(xx, cy + x as ubyte)
                plot(xx, cy - x as ubyte)
            }
            for xx in cx-y to cx {
                plot(xx, cy + x as ubyte)
                plot(xx, cy - x as ubyte)
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

    sub plot(uword px, ubyte py) {
        ; TODO put this in a tuned asm plot routine
        ; fast asm plot via lookup tables http://codebase64.org/doku.php?id=base:various_techniques_to_calculate_adresses_fast_common_screen_formats_for_pixel_graphics
        ubyte[] ormask = [128, 64, 32, 16, 8, 4, 2, 1]
        uword addr = bitmap_address + 320*(py>>3) + (py & 7) + (px & %0000001111111000)
        @(addr) |= ormask[lsb(px) & 7]
    }

}


