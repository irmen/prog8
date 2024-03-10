%import textio
%import syslib
%zeropage basicsafe

; Note: this program can be compiled for multiple target systems.

main {

    sub start() {
        txt.print("rectangles\nand circle\ndrawing.\n")

        ubyte r
        for r in 3 to 12 step 3 {
            circle(20, 12, r)
        }

        txt.print("enter for disc:")
        void cbm.CHRIN()
        txt.nl()
        txt.clear_screen()
        disc(20, 12, 12)

        txt.print("enter for rectangles:")
        void cbm.CHRIN()
        txt.nl()
        txt.clear_screen()

        rect(4, 8, 37, 23, false)
        rect(20, 12, 30, 20, true)
        rect(10, 10, 10, 10, false)
        rect(6, 0, 16, 20, true)


        sub rect(ubyte x1, ubyte y1, ubyte x2, ubyte y2, bool fill) {
            ubyte x
            ubyte y
            if fill {
                for y in y1 to y2 {
                    for x in x1 to x2 {
                        txt.setcc(x, y, 42, x+y)
                    }
                }
            } else {
                for x in x1 to x2 {
                    txt.setcc(x, y1, 42, 8)
                    txt.setcc(x, y2, 42, 8)
                }
                if y2>y1 {
                    for y in y1+1 to y2-1 {
                        txt.setcc(x1, y, 42, 7)
                        txt.setcc(x2, y, 42, 7)
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
                txt.setcc(xcenter + x, ycenter + y, 81, 1)
                txt.setcc(xcenter - x, ycenter + y, 81, 2)
                txt.setcc(xcenter + x, ycenter - y, 81, 3)
                txt.setcc(xcenter - x, ycenter - y, 81, 4)
                txt.setcc(xcenter + y, ycenter + x, 81, 5)
                txt.setcc(xcenter - y, ycenter + x, 81, 6)
                txt.setcc(xcenter + y, ycenter - x, 81, 7)
                txt.setcc(xcenter - y, ycenter - x, 81, 8)
                y++
                if decisionOver2>=0 {
                    x--
                    decisionOver2 -= 2*x
                }
                decisionOver2 += 2*y
                decisionOver2++
            }
        }

        sub disc(ubyte cx, ubyte cy, ubyte radius) {
            ; Midpoint algorithm, filled
            ubyte x = radius
            ubyte y = 0
            byte decisionOver2 = 1-x as byte
            ubyte xx

            while x>=y {
                xx = cx-x
                repeat 2*x+1 {
                    txt.setcc(xx, cy + y, 81, 11)
                    txt.setcc(xx, cy - y, 81, 12)
                    xx++
                }
                xx = cx-y
                repeat 2*y+1 {
                    txt.setcc(xx, cy + x, 81, 13)
                    txt.setcc(xx, cy - x, 81, 14)
                    xx++
                }
                y++
                if decisionOver2>=0 {
                    x--
                    decisionOver2 -= 2*x
                }
                decisionOver2 += 2*y
                decisionOver2++
            }
        }
    }
}
