%import textio
%zeropage basicsafe

main {
    const ubyte numCellsHoriz = 15 ;(screenwidth-1) / 2
    const ubyte numCellsVert = 7 ; (screenheight-1) / 2

    ; cell properties
    const ubyte RIGHT = 2
    ubyte[256] cells = 0

    sub generate() {
        ubyte cx = 0
        cells[0] = 255
        repeat 40 {
            bool fits = cx<numCellsHoriz
            if fits and not @(celladdr(cx+1)) {         ; TODO evaluated wrong in RPN! Only as part of IF, and using celladdr()
                cx++
                cells[cx] = 255
            }
        }
        txt.print_ub(cx)
        txt.print(" should be ")
        txt.print_ub(numCellsHoriz)
    }

    sub celladdr(ubyte cx) -> uword {
        return &cells+cx
    }

    sub start()  {
        generate()
        txt.nl()
        txt.nl()
    }
}

