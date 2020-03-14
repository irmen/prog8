%import c64lib
%import c64utils
%zeropage basicsafe


main {

    sub start() {
        c64scr.print("mid-point\ncircle\n and\nbresenham\nline\nalgorithms.\n")

        const ubyte xcenter = 20
        const ubyte ycenter = 12
        ubyte r
        for r in 3 to 12 step 3 {
            circle(r)
        }
        line(5,3, 30,3)
        line(5,3, 5, 24)
        line(30,3,30,24)
        line(5,24,30,24)

        line(1, 10, 38, 24)
        line(1, 20, 38, 2)
        line(20, 4, 10, 24)
        line(39, 16, 12, 0)

            ; TODO fix crash arg type incompatible c64scr.setcc(x0, y0 + radius, 81, 1)

        sub line(ubyte x1, ubyte y1, ubyte x2, ubyte y2) {
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
                ; TODO fix assembler problem when defining label here
                forever {
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
                ; TODO fix assembler problem when defining label here
                forever {
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

        sub circle(ubyte radius) {
            byte x = radius as byte
            byte y = 0
            byte decisionOver2 = 1-x

            while x>=y {
                c64scr.setcc(xcenter + x as ubyte, ycenter + y as ubyte, 81, 1)
                c64scr.setcc(xcenter - x as ubyte, ycenter + y as ubyte, 81, 1)
                c64scr.setcc(xcenter + x as ubyte, ycenter - y as ubyte, 81, 1)
                c64scr.setcc(xcenter - x as ubyte, ycenter - y as ubyte, 81, 1)
                c64scr.setcc(xcenter + y as ubyte, ycenter + x as ubyte, 81, 1)
                c64scr.setcc(xcenter - y as ubyte, ycenter + x as ubyte, 81, 1)
                c64scr.setcc(xcenter + y as ubyte, ycenter - x as ubyte, 81, 1)
                c64scr.setcc(xcenter - y as ubyte, ycenter - x as ubyte, 81, 1)
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
