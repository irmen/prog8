~ main {

    const ubyte boardOffsetX = 14
    const ubyte boardOffsetY = 3
    const ubyte boardWidth = 10
    const ubyte boardHeight = 20
    const ubyte startXpos = boardOffsetX + 3
    const ubyte startYpos = boardOffsetY - 2

    ubyte[4] blockI = [4, 5, 6, 7]      ; note: special rotation
    ubyte[4] blockJ = [0, 4, 5, 6]
    ubyte[4] blockL = [2, 4, 5, 6]
    ubyte[4] blockO = [1, 2, 5, 6]      ; note: no rotation
    ubyte[4] blockS = [1, 2, 4, 5]
    ubyte[4] blockT = [1, 4, 5, 6]
    ubyte[4] blockZ = [0, 1, 5, 6]

    ; block colors I, J, L, O, S, T, Z:  cyan, blue, orange, yellow, green, purple, red
    ubyte[7] blockColors = [3, 6, 8, 7, 5, 4, 2]

    ubyte lines = 0
    ubyte score = 0
    ubyte xpos = startXpos
    ubyte ypos = startYpos
    ubyte nextBlock = rnd() % 7

    sub start() {
        @(650) = 128        ; set all keys to repeat
        drawBoard()
        spawnNextBlock()

waitkey:
        if c64.TIME_LO==30 {
            c64.TIME_LO = 0
            if blocklogic.canMoveDown(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                ypos++ ; descend
                drawBlock(xpos, ypos, 160)
            } else {
                ; block can't move further down!
                ; check if the game area is full, if not, spawn the next block at the top.
                if blocklogic.isGameOver() {
                    c64scr.PLOT(7, 7)
                    c64.CHROUT('U')
                    c64scr.print("────────────────────────")
                    c64.CHROUT('I')
                    c64scr.PLOT(7, 8)
                    c64scr.print("│*** g a m e  o v e r ***│")
                    c64scr.PLOT(7, 9)
                    c64.CHROUT('J')
                    c64scr.print("────────────────────────")
                    c64.CHROUT('K')
                } else {
                    spawnNextBlock()
                }
            }
        }

        ubyte key=c64.GETIN()
        if_z goto waitkey

        if key>='1' and key<='7' {
            ; select block type
            drawBlock(xpos, ypos, 32)
            newCurrentBlock(key-'1')
            drawBlock(xpos, ypos, 160)
        }
        else if key==157 or key==',' {
            if blocklogic.canMoveLeft(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                xpos-- ; move left
                drawBlock(xpos, ypos, 160)
            }
        }
        else if key==29 or key=='.' {
            if blocklogic.canMoveRight(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                xpos++ ; move right
                drawBlock(xpos, ypos, 160)
            }
        }
        else if key==17 or key=='m' {
            if blocklogic.canMoveDown(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                ypos++ ; descend
                drawBlock(xpos, ypos, 160)
            }
        }
        else if key==145 or key==' ' {
            ; drop down immediately
            drawBlock(xpos, ypos, 32)
            ypos = boardOffsetY+boardHeight-4
            drawBlock(xpos, ypos, 160)
        }
        else if key=='z' {
            ; rotate CCW
            drawBlock(xpos, ypos, 32)
            blocklogic.rotateCCW()
            drawBlock(xpos, ypos, 160)
        }
        else if key=='x' {
            ; rotate CW
            drawBlock(xpos, ypos, 32)
            blocklogic.rotateCW()
            drawBlock(xpos, ypos, 160)
        }
        goto waitkey

    }

    sub spawnNextBlock() {
        c64.TIME_LO = 0
        newCurrentBlock(nextBlock)
        nextBlock = rnd() % 7
        drawNextBlock()
        xpos = startXpos
        ypos = startYpos
        drawBlock(xpos, ypos, 160)
    }

    sub drawBoard() {
        c64.COLOR = 7
        c64scr.PLOT(1,1)
        c64scr.print("irmen's")
        c64scr.PLOT(1,2)
        c64scr.print("teh▁triz")
        c64.COLOR = 5
        c64scr.PLOT(28,3)
        c64scr.print("next:")
        c64scr.PLOT(28,10)
        c64scr.print("lines:")
        c64scr.PLOT(28,14)
        c64scr.print("score:")
        c64.COLOR = 12
        c64scr.PLOT(28,19)
        c64scr.print("controls:")
        c64.COLOR = 11
        c64scr.PLOT(27,20)
        c64scr.print("z/x  rotate")
        c64scr.PLOT(27,21)
        c64scr.print(",/.  move")
        c64scr.PLOT(27,22)
        c64scr.print("spc  drop")
        c64scr.PLOT(27,23)
        c64scr.print("  m  descend")

        c64scr.setcc(boardOffsetX-1, boardOffsetY-1, 108, 12)
        c64scr.setcc(boardOffsetX+boardWidth, boardOffsetY-1, 123, 12)
        c64scr.setcc(boardOffsetX-1, boardOffsetY+boardHeight, 124, 12)
        c64scr.setcc(boardOffsetX+boardWidth, boardOffsetY+boardHeight, 126, 12)
        ubyte i
        for i in boardOffsetX+boardWidth-1 to boardOffsetX step -1
            c64scr.setcc(i, boardOffsetY+boardHeight, 69, 11)
        for i in boardOffsetY+boardHeight-1 to boardOffsetY step -1 {
            c64scr.setcc(boardOffsetX-1, i, 89, 11)
            c64scr.setcc(boardOffsetX+boardWidth, i, 84, 11)
        }

        for ubyte b in 7 to 0 step -1 {
            newCurrentBlock(b)
            drawBlock(3, 3+b*3, 102)                    ; 102 = stipple
        }
        drawScore()
    }

    sub drawScore() {
        c64.COLOR=1
        c64scr.PLOT(30,11)
        c64scr.print_ub(lines)
        c64scr.PLOT(30,15)
        c64scr.print_uw(score)
    }

    sub drawNextBlock() {
        for ubyte x in 31 to 28 step -1 {
            c64scr.setcc(x, 5, ' ', 0)
            c64scr.setcc(x, 6, ' ', 0)
        }

        ; reuse the normal block draw routine (because we can't manipulate array pointers yet)
        ubyte prev = blocklogic.currentBlockNum
        newCurrentBlock(nextBlock)
        drawBlock(28, 5, 160)
        newCurrentBlock(prev)
    }

    sub newCurrentBlock(ubyte block) {
        memset(blocklogic.currentBlock, len(blocklogic.currentBlock), 0)
        blocklogic.currentBlockNum = block

        ; @todo would be nice to have an explicit pointer type to reference the array, and code the loop only once...
        ubyte blockCol = blockColors[block]
        ubyte i
        if block==0 {        ; I
            for i in blockI
                blocklogic.currentBlock[i] = blockCol
        }
        else if block==1 {        ; J
            for i in blockJ
                blocklogic.currentBlock[i] = blockCol
        }
        else if block==2 {        ; L
            for i in blockL
                blocklogic.currentBlock[i] = blockCol
        }
        else if block==3 {        ; O
            for i in blockO
                blocklogic.currentBlock[i] = blockCol
        }
        else if block==4 {        ; S
            for i in blockS
                blocklogic.currentBlock[i] = blockCol
        }
        else if block==5 {        ; T
            for i in blockT
                blocklogic.currentBlock[i] = blockCol
        }
        else if block==6 {        ; Z
            for i in blockZ
                blocklogic.currentBlock[i] = blockCol
        }
    }

    sub drawBlock(ubyte x, ubyte y, ubyte character) {
        for ubyte i in 15 to 0 step -1 {
            ubyte c=blocklogic.currentBlock[i]
            if c
                c64scr.setcc((i&3)+x, (i/4)+y, character, c)
        }
    }
}


~ blocklogic {

    ubyte currentBlockNum
    ubyte[16] currentBlock
    ubyte[16] rotated

    sub rotateCW() {
        ; rotates the current block clockwise.
        if canRotateCW() {
            if currentBlockNum==0
                rotateIblock()    ; block 'I' has special rotation
            else if currentBlockNum!=3 {
                ; rotate all other blocks (except 3, the square) around their center square
                rotated[0] = currentBlock[8]
                rotated[1] = currentBlock[4]
                rotated[2] = currentBlock[0]
                rotated[4] = currentBlock[9]
                rotated[6] = currentBlock[1]
                rotated[8] = currentBlock[10]
                rotated[9] = currentBlock[6]
                rotated[10] = currentBlock[2]

                currentBlock[0] = rotated[0]
                currentBlock[1] = rotated[1]
                currentBlock[2] = rotated[2]
                currentBlock[4] = rotated[4]
                currentBlock[6] = rotated[6]
                currentBlock[8] = rotated[8]
                currentBlock[9] = rotated[9]
                currentBlock[10] = rotated[10]
            }
        }
    }

    sub rotateCCW() {
        ; rotates the current block counterclockwise.
        if canRotateCCW() {
            if currentBlockNum==0
                rotateIblock()    ; block 'I' has special rotation
            else if currentBlockNum!=3 {
                ; rotate all other blocks (except 3, the square) around their center square
                rotated[0] = currentBlock[2]
                rotated[1] = currentBlock[6]
                rotated[2] = currentBlock[10]
                rotated[4] = currentBlock[1]
                rotated[6] = currentBlock[9]
                rotated[8] = currentBlock[0]
                rotated[9] = currentBlock[4]
                rotated[10] = currentBlock[8]

                currentBlock[0] = rotated[0]
                currentBlock[1] = rotated[1]
                currentBlock[2] = rotated[2]
                currentBlock[4] = rotated[4]
                currentBlock[6] = rotated[6]
                currentBlock[8] = rotated[8]
                currentBlock[9] = rotated[9]
                currentBlock[10] = rotated[10]
            }
        }
    }

    sub rotateIblock() {
        ; the I-block only has 2 rotational states.
        if currentBlock[2]==0 {
            ; it's horizontal, make it vertical again
            currentBlock[2] = currentBlock[4]
            currentBlock[10] = currentBlock[4]
            currentBlock[14] = currentBlock[4]
            currentBlock[4] = 0
            currentBlock[5] = 0
            currentBlock[7] = 0
        } else {
            ; it's vertical, make it horizontal again
            currentBlock[4] = currentBlock[2]
            currentBlock[5] = currentBlock[2]
            currentBlock[7] = currentBlock[2]
            currentBlock[2] = 0
            currentBlock[10] = 0
            currentBlock[14] = 0
        }
    }

    sub canRotateCW() -> ubyte {
        return true ; TODO determine
    }

    sub canRotateCCW() -> ubyte {
        return true ; TODO determine
    }

    sub canMoveLeft(ubyte xpos, ubyte ypos) -> ubyte {
        return xpos>main.boardOffsetX      ; TODO deal with actual block collision
    }

    sub canMoveRight(ubyte xpos, ubyte ypos) -> ubyte {
        return xpos<main.boardOffsetX+main.boardWidth-4  ; TODO deal with actual block collision
    }

    sub canMoveDown(ubyte xpos, ubyte ypos) -> ubyte {
        return ypos<main.boardOffsetY+main.boardHeight-4 ; TODO deal with actual block collision
    }

    sub isGameOver() -> ubyte {
        return false     ; TODO determine fail state
    }
}
