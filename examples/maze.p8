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
    const ubyte STONE = 128
    const ubyte WALKED = 64
    const ubyte BACKTRACKED = 32
    const ubyte UP = 1
    const ubyte RIGHT = 2
    const ubyte DOWN = 4
    const ubyte LEFT = 8
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

    ubyte[4] directionflags = [LEFT,RIGHT,UP,DOWN]

    sub generate() {
        ubyte cx = startCx
        ubyte cy = startCy

        stackptr = 0
        @(celladdr(cx,cy)) &= ~STONE
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
                    if cells_to_carve {
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
                    if cells_to_carve {
                        if repath()
                            goto carve_restart_after_repath
                    }
                    return
                }
                @(celladdr(cx,cy)) |= direction
                when direction {
                    UP -> {
                        cy--
                        @(celladdr(cx,cy)) |= DOWN
                    }
                    RIGHT -> {
                        cx++
                        @(celladdr(cx,cy)) |= LEFT
                    }
                    DOWN -> {
                        cy++
                        @(celladdr(cx,cy)) |= UP
                    }
                    LEFT -> {
                        cx--
                        @(celladdr(cx,cy)) |= RIGHT
                    }
                }
                @(celladdr(cx,cy)) &= ~STONE
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
                } until not @(celladdr(cx, cy)) & STONE
                if available_uncarved()
                    return true
            }
            return false
        }

        sub available_uncarved() -> ubyte {
            ubyte candidates = 0
            if cx>0 and @(celladdr(cx-1, cy)) & STONE
                candidates |= LEFT
            if cx<numCellsHoriz-1 and @(celladdr(cx+1, cy)) & STONE
                candidates |= RIGHT
            if cy>0 and @(celladdr(cx, cy-1)) & STONE
                candidates |= UP
            if cy<numCellsVert-1 and @(celladdr(cx, cy+1)) & STONE
                candidates |= DOWN
            return candidates
        }

        sub choose_uncarved_direction() -> ubyte {
            ubyte candidates =  available_uncarved()
            if not candidates
                return 0

            repeat {
                ubyte choice = candidates & directionflags[math.rnd() & 3]
                if choice
                    return choice
            }
        }
    }

    sub openpassages() {
        ; open just a few extra passages, so that multiple routes are possible in theory.
        ubyte cell
        ubyte numpassages
        ubyte cx
        ubyte cy
        do {
            do {
                cx = math.rnd() % (numCellsHoriz-2) + 1
                cy = math.rnd() % (numCellsVert-2) + 1
            } until not @(celladdr(cx, cy)) & STONE
            ubyte direction = directionflags[math.rnd() & 3]
            if not @(celladdr(cx, cy)) & direction {
                when direction {
                    LEFT -> {
                        if not @(celladdr(cx-1,cy)) & STONE {
                            @(celladdr(cx,cy)) |= LEFT
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                    RIGHT -> {
                        if not @(celladdr(cx+1,cy)) & STONE {
                            @(celladdr(cx,cy)) |= RIGHT
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                    UP -> {
                        if not @(celladdr(cx,cy-1)) & STONE {
                            @(celladdr(cx,cy)) |= UP
                            drawCell(cx,cy)
                            numpassages++
                        }
                    }
                    DOWN -> {
                        if not @(celladdr(cx,cy+1)) & STONE {
                            @(celladdr(cx,cy)) |= DOWN
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

        @(celladdr(cx,cy)) |= WALKED
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
            if cell & UP and not @(celladdr(cx,cy-1)) & (WALKED|BACKTRACKED) {
                @(pathstack + pathstackptr) = UP
                txt.setcc(cx*2+1, cy*2, 81, 3)
                cy--
            }
            else if cell & DOWN and not @(celladdr(cx,cy+1)) & (WALKED|BACKTRACKED) {
                @(pathstack + pathstackptr) = DOWN
                txt.setcc(cx*2+1, cy*2+2, 81, 3)
                cy++
            }
            else if cell & LEFT and not @(celladdr(cx-1,cy)) & (WALKED|BACKTRACKED) {
                @(pathstack + pathstackptr) = LEFT
                txt.setcc(cx*2, cy*2+1, 81, 3)
                cx--
            }
            else if cell & RIGHT and not @(celladdr(cx+1,cy)) & (WALKED|BACKTRACKED) {
                @(pathstack + pathstackptr) = RIGHT
                txt.setcc(cx*2+2, cy*2+1, 81, 3)
                cx++
            }
            else {
                ; dead end, pop stack
                pathstackptr--
                if stackptr==65535 {
                    txt.print("no solution?!")
                    return
                }
                @(celladdr(cx,cy)) |= BACKTRACKED
                txt.setcc(cx*2+1, cy*2+1, 81, 2)
                when @(pathstack + pathstackptr) {
                    UP -> {
                        txt.setcc(cx*2+1, cy*2+2, 81, 9)
                        cy++
                    }
                    DOWN -> {
                        txt.setcc(cx*2+1, cy*2, 81, 9)
                        cy--
                    }
                    LEFT -> {
                        txt.setcc(cx*2+2, cy*2+1, 81, 9)
                        cx++
                    }
                    RIGHT -> {
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
            @(celladdr(cx,cy)) |= WALKED
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
        if doors & UP
            txt.setcc(x, y-1, ' ', EMPTYCOLOR)
        if doors & RIGHT
            txt.setcc(x+1, y, ' ', EMPTYCOLOR)
        if doors & DOWN
            txt.setcc(x, y+1, ' ', EMPTYCOLOR)
        if doors & LEFT
            txt.setcc(x-1, y, ' ', EMPTYCOLOR)
        if doors & STONE
            txt.setcc(x, y, 160, WALLCOLOR)
        else
            txt.setcc(x, y, 32, EMPTYCOLOR)

        if doors & WALKED
            txt.setcc(x, y, 81, 1)
        if doors & BACKTRACKED
            txt.setcc(x, y, 81, 2)
    }

    sub initialize() {
        sys.memset(cells, numCellsHoriz*numCellsVert, STONE)
        txt.fill_screen(160, WALLCOLOR)
        drawStartFinish()
    }

    sub drawStartFinish() {
        txt.setcc(startCx*2+1,startCy*2+1,sc:'s',5)
        txt.setcc(finishCx*2+1, finishCy*2+1, sc:'f', 13)
    }
}
