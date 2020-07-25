%import c64lib
%import c64utils
%zeropage basicsafe


main {

    sub start() {
        c64scr.print("mid-point\ncircle\n and\nbresenham\nline\nalgorithms.\n")

        ubyte r
        for r in 3 to 12 step 3 {
            circle(20, 12, r)
        }

        c64scr.print("enter for disc:")
        void c64.CHRIN()
        c64.CHROUT('\n')
        c64scr.clear_screen(' ', 1)
        disc(20, 12, 12)

        c64scr.print("enter for lines:")
        void c64.CHRIN()
        c64.CHROUT('\n')
        c64scr.clear_screen(' ', 1)

        line(1, 10, 38, 24)
        line(1, 20, 38, 2)
        line(20, 4, 10, 24)
        line(39, 16, 12, 0)

        c64scr.print("enter for rectangles:")
        void c64.CHRIN()
        c64.CHROUT('\n')
        c64scr.clear_screen(' ', 1)

        rect(4, 8, 37, 23, false)
        rect(20, 12, 30, 20, true)
        rect(10, 10, 10, 10, false)
        rect(6, 0, 16, 20, true)


        sub rect(ubyte x1, ubyte y1, ubyte x2, ubyte y2, ubyte fill) {
            ubyte x
            ubyte y
            if fill {
                for y in y1 to y2 {
                    for x in x1 to x2 {
                        c64scr.setcc(x, y, 42, x+y)
                    }
                }
            } else {
                for x in x1 to x2 {
                    c64scr.setcc(x, y1, 42, 8)
                    c64scr.setcc(x, y2, 42, 8)
                }
                if y2>y1 {
                    for y in y1+1 to y2-1 {
                        c64scr.setcc(x1, y, 42, 7)
                        c64scr.setcc(x2, y, 42, 7)
                    }
                }
            }
        }

        sub line(ubyte x1, ubyte y1, ubyte x2, ubyte y2) {
            ; Bresenham algorithm, not very optimized to keep clear code.
            ; For a better optimized version have a look in the graphics.p8 module.
            byte d = 0
            ubyte dx = abs(x2 - x1)
            ubyte dy = abs(y2 - y1)
            ubyte dx2 = 2 * dx
            ubyte dy2 = 2 * dy
            ubyte ix = sgn(x2 as byte - x1 as byte) as ubyte
            ubyte iy = sgn(y2 as byte - y1 as byte) as ubyte
            ubyte x = x1
            ubyte y = y1

            if dx >= dy {
                repeat {
                    c64scr.setcc(x, y, 42, 5)
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
                repeat {
                    c64scr.setcc(x, y, 42, 5)
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

        sub circle(ubyte xcenter, ubyte ycenter, ubyte radius) {
            ; Midpoint algorithm
            ubyte x = radius
            ubyte y = 0
            byte decisionOver2 = 1-x as byte

            while x>=y {
                c64scr.setcc(xcenter + x, ycenter + y as ubyte, 81, 1)
                c64scr.setcc(xcenter - x, ycenter + y as ubyte, 81, 2)
                c64scr.setcc(xcenter + x, ycenter - y as ubyte, 81, 3)
                c64scr.setcc(xcenter - x, ycenter - y as ubyte, 81, 4)
                c64scr.setcc(xcenter + y, ycenter + x as ubyte, 81, 5)
                c64scr.setcc(xcenter - y, ycenter + x as ubyte, 81, 6)
                c64scr.setcc(xcenter + y, ycenter - x as ubyte, 81, 7)
                c64scr.setcc(xcenter - y, ycenter - x as ubyte, 81, 8)
                y++
                if decisionOver2<=0
                    decisionOver2 += 2*y+1
                else {
                    x--
                    decisionOver2 += 2*(y-x)+1
                }
            }
        }

        sub disc(ubyte cx, ubyte cy, ubyte radius) {
            ; Midpoint algorithm, filled
            ubyte x = radius
            ubyte y = 0
            byte decisionOver2 = 1-x as byte
            ubyte xx

            while x>=y {
                for xx in cx to cx+x {
                    c64scr.setcc(xx, cy + y as ubyte, 81, 1)
                    c64scr.setcc(xx, cy - y as ubyte, 81, 2)
                }
                for xx in cx-x to cx-1 {
                    c64scr.setcc(xx, cy + y as ubyte, 81, 3)
                    c64scr.setcc(xx, cy - y as ubyte, 81, 4)
                }
                for xx in cx to cx+y {
                    c64scr.setcc(xx, cy + x as ubyte, 81, 5)
                    c64scr.setcc(xx, cy - x as ubyte, 81, 6)
                }
                for xx in cx-y to cx {
                    c64scr.setcc(xx, cy + x as ubyte, 81, 7)
                    c64scr.setcc(xx, cy - x as ubyte, 81, 8)
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
    }
}
