%import textio
%import math

; Prog8 port of examples/maze.p8 to the m68k targets (qemu68k and amiga500).
; A depth-first maze generation algorithm (1 possible path from start to finish),
; and a depth-first maze solver algorithm.
; The original needed explicit stacks for this, because the 8-bit targets have
; no usable hardware stack for recursion.  The m68k does, so here both algorithms
; are written as plain recursive routines.
; The maze is printed as ordinary left-to-right text, so no cursor positioning
; is needed (and nothing is cleared or overwritten).
; Note: this program can be compiled for the various m68k target systems.

main {
    sub start()  {
        if sys.stack_size()<13900 {
            txt.print("this program needs at least 14000 bytes stack size to run\n")
            sys.exit(1)
        }

        math.rndseed($1234, $9bdf)     ; fixed seed, so the maze is deterministic
        maze.initialize()
        bool found = maze.solve()
        maze.draw()
        if found {
            txt.print("found! path length: ")
            txt.print_uw(maze.pathlength)
        } else
            txt.print("no solution?!")
        txt.nl()
        sys.exit(0)
    }
}

maze {
    ; The serial targets have a fixed 80x25 terminal, so the cell grid is 39x12.
    ; Cells go on the even screen rows/columns, so the walls around them (on the
    ; odd ones) stay inside the screen.  Every maze line is 1 + 39*2 = 79 characters.
    const ubyte screenwidth = 60
    const ubyte screenheight = 24

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

    ; The cells fit in a regular array on this target, so no memory() needed.
    ubyte[numCellsHoriz*numCellsVert] cells
    ; Direction of the step the solution takes from each cell, 0 on cells not on it.
    ubyte[numCellsHoriz*numCellsVert] pathdirs
    ; Breadth-first search queue, used by the shortest-path solver.
    uword[numCellsHoriz*numCellsVert] solveQueue
    ; Temporary storage for the shortest path during reconstruction.
    uword[numCellsHoriz*numCellsVert] pathCells

    ubyte[4] directionflags = [Cell::LEFT,Cell::RIGHT,Cell::UP,Cell::DOWN]

    uword pathlength

    ; the index arithmetic must be done in uword: numCellsHoriz*cy overflows a ubyte
    inline sub cellidx(ubyte cx, ubyte cy) -> uword {
        return cx + (numCellsHoriz as uword)*cy
    }

    sub opposite(ubyte direction) -> ubyte {
        when direction {
            Cell::UP -> return Cell::DOWN
            Cell::RIGHT -> return Cell::LEFT
            Cell::DOWN -> return Cell::UP
            Cell::LEFT -> return Cell::RIGHT
        }
        return 0
    }

    sub initialize() {
        sys.memset(cells, len(cells), Cell::STONE)
        sys.memset(pathdirs, len(pathdirs), 0)
        generate()
        openpassages()
    }

    sub generate() {
        cells[cellidx(startCx, startCy)] &= ~Cell::STONE
        carve(startCx, startCy)
    }

    ; Recursion depth is at most the number of cells, which the m68k stack handles easily.
    sub carve(ubyte cx, ubyte cy) {
        repeat {
            ubyte direction = choose_uncarved_direction(cx, cy)
            if direction==0
                return         ; nothing left to carve from here, backtrack

            cells[cellidx(cx, cy)] |= direction
            ubyte nx = cx
            ubyte ny = cy
            when direction {
                Cell::UP -> ny--
                Cell::RIGHT -> nx++
                Cell::DOWN -> ny++
                Cell::LEFT -> nx--
            }
            cells[cellidx(nx, ny)] |= opposite(direction)
            cells[cellidx(nx, ny)] &= ~Cell::STONE

            carve(nx, ny)
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
            } until cells[cellidx(cx, cy)] & Cell::STONE ==0
            ubyte direction = directionflags[math.rnd() & 3]
            if cells[cellidx(cx, cy)] & direction == 0 {
                when direction {
                    Cell::LEFT -> {
                        if cells[cellidx(cx-1,cy)] & Cell::STONE == 0 {
                            cells[cellidx(cx,cy)] |= Cell::LEFT
                            cells[cellidx(cx-1,cy)] |= Cell::RIGHT
                            numpassages++
                        }
                    }
                    Cell::RIGHT -> {
                        if cells[cellidx(cx+1,cy)] & Cell::STONE == 0 {
                            cells[cellidx(cx,cy)] |= Cell::RIGHT
                            cells[cellidx(cx+1,cy)] |= Cell::LEFT
                            numpassages++
                        }
                    }
                    Cell::UP -> {
                        if cells[cellidx(cx,cy-1)] & Cell::STONE == 0 {
                            cells[cellidx(cx,cy)] |= Cell::UP
                            cells[cellidx(cx,cy-1)] |= Cell::DOWN
                            numpassages++
                        }
                    }
                    Cell::DOWN -> {
                        if cells[cellidx(cx,cy+1)] & Cell::STONE == 0 {
                            cells[cellidx(cx,cy)] |= Cell::DOWN
                            cells[cellidx(cx,cy+1)] |= Cell::UP
                            numpassages++
                        }
                    }
                }
            }
        } until numpassages==10
    }

    sub available_uncarved(ubyte cx, ubyte cy) -> ubyte {
        ubyte candidates = 0
        if cx>0 and cells[cellidx(cx-1, cy)] & Cell::STONE !=0
            candidates |= Cell::LEFT
        if cx<numCellsHoriz-1 and cells[cellidx(cx+1, cy)] & Cell::STONE !=0
            candidates |= Cell::RIGHT
        if cy>0 and cells[cellidx(cx, cy-1)] & Cell::STONE !=0
            candidates |= Cell::UP
        if cy<numCellsVert-1 and cells[cellidx(cx, cy+1)] & Cell::STONE !=0
            candidates |= Cell::DOWN
        return candidates
    }

    sub choose_uncarved_direction(ubyte cx, ubyte cy) -> ubyte {
        ubyte candidates = available_uncarved(cx, cy)
        if candidates==0
            return 0

        repeat {
            ubyte choice = candidates & directionflags[math.rnd() & 3]
            if choice!=0
                return choice
        }
    }

    sub solve() -> bool {
        return bfs()
    }

    ; Breadth-first search to find the shortest path from start to finish.
    ; Uses index arithmetic instead of cellidx() to avoid subroutine-call overhead.
    ; pathdirs[] is filled with back-pointers during the search, then reconstructed
    ; into forward directions.  The WALKED bit in cells[] serves as the visited set.
    sub bfs() -> bool {
        uword qhead = 0
        uword qtail = 0
        const uword H = numCellsHoriz as uword
        const uword startIdx = (startCx as uword) + H * (startCy as uword)
        const uword finishIdx = (finishCx as uword) + H * (finishCy as uword)

        uword i
        for i in 0 to len(cells)-1 {
            cells[i] &= ~(Cell::WALKED | Cell::BACKTRACKED)
            pathdirs[i] = 0
        }

        solveQueue[qtail] = startIdx
        qtail++
        cells[startIdx] |= Cell::WALKED

        while qhead != qtail {
            uword idx = solveQueue[qhead]
            qhead++

            if idx==finishIdx {
                pathlength = reconstructPath(H)
                return true
            }

            ubyte cell = cells[idx]
            if cell & Cell::UP !=0 and cells[idx - H] & Cell::WALKED ==0 {
                cells[idx - H] |= Cell::WALKED
                pathdirs[idx - H] = Cell::DOWN
                solveQueue[qtail] = idx - H
                qtail++
            }
            if cell & Cell::DOWN !=0 and cells[idx + H] & Cell::WALKED ==0 {
                cells[idx + H] |= Cell::WALKED
                pathdirs[idx + H] = Cell::UP
                solveQueue[qtail] = idx + H
                qtail++
            }
            if cell & Cell::LEFT !=0 and cells[idx - 1] & Cell::WALKED ==0 {
                cells[idx - 1] |= Cell::WALKED
                pathdirs[idx - 1] = Cell::RIGHT
                solveQueue[qtail] = idx - 1
                qtail++
            }
            if cell & Cell::RIGHT !=0 and cells[idx + 1] & Cell::WALKED ==0 {
                cells[idx + 1] |= Cell::WALKED
                pathdirs[idx + 1] = Cell::LEFT
                solveQueue[qtail] = idx + 1
                qtail++
            }
        }
        return false
    }

    ; Convert the back-pointers left by bfs() into forward directions.
    ; H is numCellsHoriz; using index arithmetic avoids cellidx()/division.
    ; Returns the number of steps from start to finish.
    sub reconstructPath(uword H) -> uword {
        uword idx = (finishCx as uword) + H * (finishCy as uword)
        uword count = 0
        while idx != (startCx as uword) + H * (startCy as uword) {
            pathCells[count] = idx
            count++
            ubyte backdir = pathdirs[idx]
            when backdir {
                Cell::UP -> idx -= H
                Cell::DOWN -> idx += H
                Cell::LEFT -> idx--
                Cell::RIGHT -> idx++
            }
        }
        pathCells[count] = idx
        count++

        ; Only the cells on the shortest path should keep a direction.
        uword i
        for i in 0 to len(pathdirs)-1
            pathdirs[i] = 0

        ; pathCells is stored from finish back to start; set forward directions.
        for i in 0 to count-2 {
            uword current = pathCells[count-1-i]
            uword next = pathCells[count-2-i]
            if next == current + 1
                pathdirs[current] = Cell::RIGHT
            else if current == next + 1
                pathdirs[current] = Cell::LEFT
            else if next == current + H
                pathdirs[current] = Cell::DOWN
            else
                pathdirs[current] = Cell::UP
        }
        return count - 1
    }

    ; ---- drawing: '#' walls, ' ' passages, ASCII arrows for the solution path ----

    sub draw() {
        ubyte gy
        for gy in 0 to 2*numCellsVert {
            ubyte gx
            for gx in 0 to 2*numCellsHoriz {
                txt.chrout(gridChar(gx, gy))
            }
            txt.nl()
        }
    }

    sub gridChar(ubyte gx, ubyte gy) -> ubyte {
        ubyte wx
        ubyte wy
        bool onBorder = gx==0 or gx==2*numCellsHoriz or gy==0 or gy==2*numCellsVert
        if gx & 1 == 0 {
            if gy & 1 == 0
                return '#'              ; corner post
            ; horizontal wall segment (gx is even, gy is odd)
            if onBorder
                return '#'              ; top/bottom border
            wx = gx / 2
            wy = gy / 2
            ; wall between cell (wx-1, wy) and cell (wx, wy)
            if cells[cellidx(wx-1, wy)] & Cell::RIGHT !=0
                return ' '              ; open passage rightward
            return '#'
        }
        if gy & 1 == 0 {
            ; vertical wall segment (gx is odd, gy is even)
            if onBorder
                return '#'              ; left/right border
            wx = gx / 2
            wy = gy / 2
            ; wall between cell (wx, wy-1) and cell (wx, wy)
            if cells[cellidx(wx, wy-1)] & Cell::DOWN !=0
                return ' '              ; open passage downward
            return '#'
        }
        ; cell position (gx and gy are both odd)
        wx = gx / 2
        wy = gy / 2
        if wx==startCx and wy==startCy
            return 'S'
        if wx==finishCx and wy==finishCy
            return 'F'
        if cells[cellidx(wx, wy)] & Cell::STONE !=0
            return '#'
        when pathdirs[cellidx(wx, wy)] {
            Cell::UP -> return '^'
            Cell::RIGHT -> return '>'
            Cell::DOWN -> return 'v'
            Cell::LEFT -> return '<'
        }
        return ' '
    }
}
