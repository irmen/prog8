%import c64lib
%import c64utils
%zeropage basicsafe

; TODO WHY DOES THIS GENERATE INVALID ASSEMBLY AT THE A=@(addr) line in Plot() ????


main {

    sub start() {
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

        for iter in 0 to 150 {
            plot(xx, yy, color)
        }
    }


    sub circle(uword xcenter, ubyte ycenter, ubyte radius, ubyte color) {
        ubyte x = radius
        ubyte y = 0
        byte decisionOver2 = 1-x

        while x>=y {
            y++
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
                plot(100, 100, 2)
            }
        }
    }

    sub plot(uword px, ubyte py, ubyte color) {
        uword addr = 320
        A=@(addr)           ; TODO invalid assemlby generated    lda (addr),y    something to do with zeropage allocation????
        @(addr) = A
    }

}


