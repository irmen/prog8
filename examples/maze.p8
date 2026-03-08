%import textio
%import math

; Even though prog8 only has support for extremely limited recursion,
; you can write recursive algorithms with a bit of extra work by building your own explicit stack structure.
; This program shows a depth-first maze generation algorithm (1 possible path from start to finish),
; and a depth-first maze solver algorithm, both using a stack to store the path taken.

; Note: this program can be compiled for multiple target systems.

main {
    sub start()  {
        repeat {
            maze.initialize()
            maze.drawStartFinish()
            maze.generate()
            maze.openpassages()
            maze.drawStartFinish()
            maze.solve()
            maze.drawStartFinish()

            txt.print(" enter=new maze")
            void cbm.CHRIN()
        }
    }

}

maze {
    const uword screenwidth = txt.DEFAULT_WIDTH
    const uword screenheight = txt.DEFAULT_HEIGHT

    const ubyte numCellsHoriz = (screenwidth-1) / 2
    const ubyte numCellsVert = (screenheight-1) / 2

    ; maze start and finish cells
    const ubyte startCx = 0
    const ubyte startCy = 0
    const ubyte finishCx = numCellsHoriz-1
    const ubyte finishCy = numCellsVert-1

    ; cell properties
    enum Cell {
        UP = 1,
        RIGHT = 2,
        DOWN = 4,
        LEFT = 8,
        BACKTRACKED = 32,
        WALKED = 64,
        STONE = 128,
    }

    const ubyte WALLCOLOR = 12
    const ubyte EMPTYCOLOR = 0

    ; unfortunately on larger screens (cx16), the number of cells exceeds 256 and doesn't fit in a regular array anymore.
    uword cells = memory("cells", numCellsHoriz*numCellsVert, 0)

    ubyte[256] cx_stack
    ubyte[256] cy_stack
    ubyte stackptr

    sub draw() {
        ubyte cx
        ubyte cy
        for cx in 0 to numCellsHoriz-1 {
            for cy in 0 to numCellsVert-1 {
                drawCell(cx, cy)
            }
        }
    }

    ubyte[4] directionflags = [Cell::LEFT,Cell::RIGHT,Cell::UP,Cell::DOWN]

    sub generate() {
        ubyte cx = startCx
        ubyte cy = startCy

        stackptr = 0
        @(celladdr(cx,cy)) &= ~Cell::STONE
        drawCell(cx, cy)
        uword cells_to_carve = numCellsHoriz * numCellsVert - 1

        repeat {
carve_restart_after_repath:
            ubyte direction = choose_uncarved_direction()
            if direction==0 {
                ;backtrack
                stackptr--
                if stackptr==255 {
                    ; stack empty.
                    ; repath if we are not done yet. (this is a workaround for the prog8 256 array lenght limit)
                    if cells_to_carve!=0 {
                        if repath()
                            goto carve_restart_after_repath
                    }
                    return
                }
                cx = cx_stack[stackptr]
                cy = cy_stack[stackptr]
            } else {
                cx_stack[stackptr] = cx
                cy_stack[stackptr] = cy
                stackptr++
                if stackptr==0 {
                    ; stack overflow, we can't track our path any longer.
                    ; repath if we are not done yet. (this is a workaround for the prog8 256 array lenght limit)
                    if cells_to_carve!=0 {
                        if repath()
                            goto carve_restart_after_repath
                    }
                    return
                }
                @(celladdr(cx,cy)) |= direction
                when direction {
                    Cell::UP -> {
                        cy--
                        @(celladdr(cx,cy)) |= Cell::DOWN
                    }
                    Cell::RIGHT -> {
                        cx++
                        @(celladdr(cx,cy)) |= Cell::LEFT
                    }
                    Cell::DOWN -> {
                        cy++
                        @(celladdr(cx,cy)) |= Cell::UP
                    }
                    Cell::LEFT -> {
                        cx--
                        @(celladdr(cx,cy)) |= Cell::RIGHT
                    }
                }
                @(celladdr(cx,cy)) &= ~Cell::STONE
                cells_to_carve--
                drawCell(cx, cy)
            }
        }

        sub repath() -> bool {
            ; repath: try to find a new start cell with possible directions.
            ; we limit our number of searches so that the algorith doesn't get stuck
            ; for too long on bad rng... just accept a few unused cells in that case.
            repeat 255 {
                do {
                    cx = math.rnd() % numCellsHoriz
                    cy = math.rnd() % numCellsVert
                } until @(celladdr(cx, cy)) & Cell::STONE ==0
                if available_uncarved()!=0
                    return true
            }
            return false
        }

        sub available_uncarved() -> ubyte {
            ubyte candidates = 0
            if cx>0 and @(celladdr(cx-1, cy)) & Cell::STONE !=0
                candidates |= Cell::LEFT
            if cx<numCellsHoriz-1 and @(celladdr(cx+1, cy)) & Cell::STONE !=0
                candidates |= Cell::RIGHT
            if cy>0 and @(celladdr(cx, cy-1)) & Cell::STONE !=0
                candidates |= Cell::UP
            if cy<numCellsVert-1 and @(celladdr(cx, cy+1)) & Cell::STONE !=0
                candidates |= Cell::DOWN
            return candidates
        }

        sub choose_uncarved_direction() -> ubyte {
            ubyte candidates =  available_uncarved()
            if candidates==0
                return 0

            repeat {
                ubyte choice = candidates & directionflags[math.rnd() & 3]
                if choice!=0
                    return choice
            }
        }
    }

    sub openpassages() {
        ; open just a few extra passages, so that multiple routes are possible in theory.
        ubyte numpassages
        ubyte cx
        ubyte cy
        do {
            do {
                cx = math.rnd() % (numCellsHoriz-2) + 1
                cy = math.rnd() % (numCellsVert-2) + 1
            } until @(celladdr(cx, cy)) & Cell::STONE ==0
            ubyte direction = directionflags[math.rnd() & 3]
            if @(celladdr(cx, cy)) & direction == 0 {
                when direction {
                    Cell::LEFT -> {
                        if @(celladdr(cx-1,cy)) & Cell::STONE == 0 {
                            @(celladdr(cx,cy)) |= Cell::LEFT
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                    Cell::RIGHT -> {
                        if @(celladdr(cx+1,cy)) & Cell::STONE == 0 {
                            @(celladdr(cx,cy)) |= Cell::RIGHT
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                    Cell::UP -> {
                        if @(celladdr(cx,cy-1)) & Cell::STONE == 0 {
                            @(celladdr(cx,cy)) |= Cell::UP
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                    Cell::DOWN -> {
                        if @(celladdr(cx,cy+1)) & Cell::STONE == 0 {
                            @(celladdr(cx,cy)) |= Cell::DOWN
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                }
            }
        } until numpassages==10
    }

    sub solve() {
        ubyte cx = startCx
        ubyte cy = startCy
        const uword max_path_length = 1024

        ; the path through the maze can be longer than 256 so doesn't fit in a regular array.... :(
        uword pathstack = memory("pathstack", max_path_length, 0)
        uword pathstackptr = 0

        @(celladdr(cx,cy)) |= Cell::WALKED
        txt.setcc(cx*2+1, cy*2+1, 81, 1)

        repeat {
solve_loop:
            sys.waitvsync()
            if cx==finishCx and cy==finishCy {
                txt.home()
                txt.print("found! path length: ")
                txt.print_uw(pathstackptr)
                return
            }

            ubyte cell = @(celladdr(cx,cy))
            if cell & Cell::UP!=0 and @(celladdr(cx,cy-1)) & (Cell::WALKED|Cell::BACKTRACKED) ==0 {
                @(pathstack + pathstackptr) = Cell::UP
                txt.setcc(cx*2+1, cy*2, 81, 3)
                cy--
            }
            else if cell & Cell::DOWN !=0 and @(celladdr(cx,cy+1)) & (Cell::WALKED|Cell::BACKTRACKED) ==0 {
                @(pathstack + pathstackptr) = Cell::DOWN
                txt.setcc(cx*2+1, cy*2+2, 81, 3)
                cy++
            }
            else if cell & Cell::LEFT !=0 and @(celladdr(cx-1,cy)) & (Cell::WALKED|Cell::BACKTRACKED) ==0 {
                @(pathstack + pathstackptr) = Cell::LEFT
                txt.setcc(cx*2, cy*2+1, 81, 3)
                cx--
            }
            else if cell & Cell::RIGHT !=0 and @(celladdr(cx+1,cy)) & (Cell::WALKED|Cell::BACKTRACKED) ==0 {
                @(pathstack + pathstackptr) = Cell::RIGHT
                txt.setcc(cx*2+2, cy*2+1, 81, 3)
                cx++
            }
            else {
                ; dead end, pop stack
                pathstackptr--
                if pathstackptr==65535 {
                    txt.print("no solution?!")
                    return
                }
                @(celladdr(cx,cy)) |= Cell::BACKTRACKED
                txt.setcc(cx*2+1, cy*2+1, 81, 2)
                when @(pathstack + pathstackptr) {
                    Cell::UP -> {
                        txt.setcc(cx*2+1, cy*2+2, 81, 9)
                        cy++
                    }
                    Cell::DOWN -> {
                        txt.setcc(cx*2+1, cy*2, 81, 9)
                        cy--
                    }
                    Cell::LEFT -> {
                        txt.setcc(cx*2+2, cy*2+1, 81, 9)
                        cx++
                    }
                    Cell::RIGHT -> {
                        txt.setcc(cx*2, cy*2+1, 81, 9)
                        cx--
                    }
                }
                goto solve_loop
            }
            pathstackptr++
            if pathstackptr==max_path_length {
                txt.print("stack overflow, path too long")
                return
            }
            @(celladdr(cx,cy)) |= Cell::WALKED
            txt.setcc(cx*2+1, cy*2+1, 81, 1)
        }
    }

    sub celladdr(ubyte cx, ubyte cy) -> uword {
        return cells+(numCellsHoriz as uword)*cy+cx
    }

    sub drawCell(ubyte cx, ubyte cy) {
        ubyte x = cx * 2 + 1
        ubyte y = cy * 2 + 1
        ubyte doors = @(celladdr(cx,cy))
        if doors & Cell::UP !=0
            txt.setcc(x, y-1, ' ', EMPTYCOLOR)
        if doors & Cell::RIGHT !=0
            txt.setcc(x+1, y, ' ', EMPTYCOLOR)
        if doors & Cell::DOWN !=0
            txt.setcc(x, y+1, ' ', EMPTYCOLOR)
        if doors & Cell::LEFT !=0
            txt.setcc(x-1, y, ' ', EMPTYCOLOR)
        if doors & Cell::STONE !=0
            txt.setcc(x, y, 160, WALLCOLOR)
        else
            txt.setcc(x, y, 32, EMPTYCOLOR)

        if doors & Cell::WALKED !=0
            txt.setcc(x, y, 81, 1)
        if doors & Cell::BACKTRACKED !=0
            txt.setcc(x, y, 81, 2)
    }

    sub initialize() {
        sys.memset(cells, numCellsHoriz*numCellsVert, Cell::STONE)
        txt.fill_screen(160, WALLCOLOR)
        drawStartFinish()
    }

    sub drawStartFinish() {
        txt.setcc(startCx*2+1,startCy*2+1,sc:'s',5)
        txt.setcc(finishCx*2+1, finishCy*2+1, sc:'f', 13)
    }
}
