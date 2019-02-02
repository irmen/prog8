~ main {

    const ubyte boardOffsetX = 14
    const ubyte boardOffsetY = 3
    const ubyte boardWidth = 10
    const ubyte boardHeight = 20

    ; 3x3, rotating around their center square:
    ubyte[4] blockJ = [0, 4, 5, 6]
    ubyte[4] blockL = [2, 4, 5, 6]
    ubyte[4] blockS = [1, 2, 4, 5]
    ubyte[4] blockT = [1, 4, 5, 6]
    ubyte[4] blockZ = [0, 1, 5, 6]
    ;4x4, rotating around center:
    ubyte[4] blockI = [4, 5, 6, 7]
    ubyte[4] blockO = [1, 2, 5, 6]

    ; block colors I, J, L, O, S, T, Z:  cyan, blue, orange, yellow, green, purple, red
    ubyte[7] blockColors = [3, 6, 8, 7, 5, 4, 2]
    ubyte[7] blockSizes = [4, 3, 3, 4, 3, 3, 3]  ;  needed for proper rotation? (or just use block num?)

    ubyte[16] currentBlock
    ubyte currentBlockSize      ; 3 or 4
    ubyte currentBlockNum

    sub start() {
        drawBoard()

        for ubyte b in 7 to 0 step -1 {
            newCurrentBlock(b)
            drawBlock(3, 2+b*3, 102)                    ; 102 = stipple
            drawBlock(boardOffsetX+3, 1+b*3, 160)       ; 160 = block,   32 = erase (space)
        }

        while(true) {
            ; loop
        }
    }

    sub drawBoard() {
        c64scr.PLOT(1,1)
        c64scr.print("teh▁triz")
        c64scr.setcc(boardOffsetX-1, boardOffsetY+boardHeight, 124, 12)
        c64scr.setcc(boardOffsetX+boardWidth, boardOffsetY+boardHeight, 126, 12)
        ubyte i
        for i in boardOffsetX+boardWidth-1 to boardOffsetX step -1
            c64scr.setcc(i, boardOffsetY+boardHeight, 69, 11)
        for i in boardOffsetY+boardHeight-1 to boardOffsetY step -1 {
            c64scr.setcc(boardOffsetX-1, i, 89, 11)
            c64scr.setcc(boardOffsetX+boardWidth, i, 84, 11)
        }
    }


    sub newCurrentBlock(ubyte block) {
        memset(currentBlock, len(currentBlock), 0)
        currentBlockNum = block
        currentBlockSize = blockSizes[block]

        ; @todo would be nice to have an explicit pointer type to reference the array, and code the loop only once...
        ubyte blockCol = blockColors[block]
        ubyte i
        if block==0 {        ; I
            for i in blockI
                currentBlock[i] = blockCol
        }
        else if block==1 {        ; J
            for i in blockJ
                currentBlock[i] = blockCol
        }
        else if block==2 {        ; L
            for i in blockL
                currentBlock[i] = blockCol
        }
        else if block==3 {         ; O
            for i in blockO
                currentBlock[i] = blockCol
        }
        else if block==4 {        ; S
            for i in blockS
                currentBlock[i] = blockCol
        }
        else if block==5 {        ; T
            for i in blockT
                currentBlock[i] = blockCol
        }
        else if block==6 {        ; Z
            for i in blockZ
                currentBlock[i] = blockCol
        }
    }

    sub drawBlock(ubyte x, ubyte y, ubyte character) {
        for ubyte i in 15 to 0 step -1 {
            ubyte c=currentBlock[i]
            if c
                c64scr.setcc((i&3)+x, (i/4)+y, character, c)
        }
    }
}
