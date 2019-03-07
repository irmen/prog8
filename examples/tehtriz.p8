
; TehTriz - a Tetris clone.
;

; @todo: holding a block.
; @todo: show next 2 blocks instead of just 1.
; @todo: how to deal with rotation when block is against a wall or another block ('bump' off the wall?)
; @todo: should not give the same block more than twice in a row?
; @todo: simple sound effects?  slight click when moving, swish when rotating/dropping, soft explosion when lines are cleared, buzz at game over


~ main {

    const ubyte boardOffsetX = 14
    const ubyte boardOffsetY = 3
    const ubyte boardWidth = 10
    const ubyte boardHeight = 20
    const ubyte startXpos = boardOffsetX + 3
    const ubyte startYpos = boardOffsetY - 2

    ubyte lines = 0
    uword score = 0
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

                ; slowly move the block down
                drawBlock(xpos, ypos, 32)
                ypos++
                drawBlock(xpos, ypos, 160)

            } else {
                ; block can't move further down!
                ; check if the game area is full, if not, spawn the next block at the top.
                if blocklogic.isGameOver(xpos, ypos) {
                    game_over()
                } else {
                    spawnNextBlock()
                }
            }
        }

        ubyte key=c64.GETIN()   ; @todo: joystick support as well. (doesn't joy1 input characters as well?)
        if_z goto waitkey

        if key>='1' and key<='7' {
            ; select block type, reset to start pos
            ; @todo remove this feature it is for testing purposes only
            xpos = startXpos
            ypos = startYpos
            drawBlock(xpos, ypos, 32)
            blocklogic.newCurrentBlock(key-'1')
            drawBlock(xpos, ypos, 160)
        }
        else if key==157 or key==',' {
            ; move left
            if blocklogic.canMoveLeft(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                xpos--
                drawBlock(xpos, ypos, 160)
            }
        }
        else if key==29 or key=='.' {
            ; move right
            if blocklogic.canMoveRight(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                xpos++
                drawBlock(xpos, ypos, 160)
            }
        }
        else if key==17 or key=='m' {
            ; move down faster
            if blocklogic.canMoveDown(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                ypos++
                drawBlock(xpos, ypos, 160)
            }
        }
        else if key==145 or key==' ' {
            ; drop down immediately
            drawBlock(xpos, ypos, 32)
            ypos = boardOffsetY+boardHeight-4  ; @todo determine proper y position
            drawBlock(xpos, ypos, 160)
        }
        else if key=='z' {
            ; rotate counter clockwise
            if blocklogic.canRotateCCW(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                blocklogic.rotateCCW()
                drawBlock(xpos, ypos, 160)
            }
        }
        else if key=='x' {
            ; rotate clockwise
            if blocklogic.canRotateCW(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                blocklogic.rotateCW()
                drawBlock(xpos, ypos, 160)
            }
        }

        ; @todo check if line(s) are full -> flash/clear line(s) + add score + move rest down

        goto waitkey

    }

    sub game_over() {
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
        while(true) {
            ; endless loop
            ; @todo restart game on pressing F1/firebutton
        }
    }

    sub spawnNextBlock() {
        c64.TIME_LO = 0
        blocklogic.newCurrentBlock(nextBlock)
        nextBlock = (rnd() + c64.RASTER) % 7
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

        c64scr.setcc(boardOffsetX-1, boardOffsetY-2, 255, 0)           ; invisible barrier
        c64scr.setcc(boardOffsetX-1, boardOffsetY-3, 255, 0)           ; invisible barrier
        c64scr.setcc(boardOffsetX+boardWidth, boardOffsetY-2, 255, 0)  ; invisible barrier
        c64scr.setcc(boardOffsetX+boardWidth, boardOffsetY-3, 255, 0)  ; invisible barrier

        c64scr.setcc(boardOffsetX-1, boardOffsetY-1, 108, 12)
        c64scr.setcc(boardOffsetX+boardWidth, boardOffsetY-1, 123, 12)
        c64scr.setcc(boardOffsetX+boardWidth, boardOffsetY-1, 123, 12)
        c64scr.setcc(boardOffsetX-1, boardOffsetY+boardHeight, 124, 12)
        c64scr.setcc(boardOffsetX+boardWidth, boardOffsetY+boardHeight, 126, 12)
        ubyte i
        for i in boardOffsetX+boardWidth-1 to boardOffsetX step -1 {
            c64scr.setcc(i, boardOffsetY-3, 255, 0)           ; invisible barrier
            c64scr.setcc(i, boardOffsetY+boardHeight, 69, 11)
        }
        for i in boardOffsetY+boardHeight-1 to boardOffsetY step -1 {
            c64scr.setcc(boardOffsetX-1, i, 89, 11)
            c64scr.setcc(boardOffsetX+boardWidth, i, 84, 11)
        }

        for i in 7 to 0 step -1 {
            blocklogic.newCurrentBlock(i)
            drawBlock(3, 3+i*3, 102)                    ; 102 = stipple
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
        blocklogic.newCurrentBlock(nextBlock)
        drawBlock(28, 5, 160)
        blocklogic.newCurrentBlock(prev)
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

    ; the 7 tetrominos
    ubyte[16] blockI = [0,0,0,0,        ; cyan ; note: special rotation (only 2 states)
                        3,3,3,3,
                        0,0,0,0,
                        0,0,0,0]
    ubyte[16] blockJ = [6,0,0,0,        ; blue
                        6,6,6,0,
                        0,0,0,0,
                        0,0,0,0]
    ubyte[16] blockL = [0,0,8,0,        ; orange
                        8,8,8,0,
                        0,0,0,0,
                        0,0,0,0]
    ubyte[16] blockO = [0,7,7,0,        ; yellow ; note: no rotation (square)
                        0,7,7,0,
                        0,0,0,0,
                        0,0,0,0]
    ubyte[16] blockS = [0,5,5,0,        ; green
                        5,5,0,0,
                        0,0,0,0,
                        0,0,0,0]
    ubyte[16] blockT = [0,4,0,0,        ; purple
                        4,4,4,0,
                        0,0,0,0,
                        0,0,0,0]
    ubyte[16] blockZ = [2,2,0,0,        ; red
                        0,2,2,0,
                        0,0,0,0,
                        0,0,0,0]

    ; @todo would be nice to have a pointer type, like so:
    ;  uword[7] blocks = [&blockI, &blockJ, &blockL, &blockO, &blockS, &blockT, &blockZ]

    sub newCurrentBlock(ubyte block) {
        currentBlockNum = block
        if block==0
            memcopy(blockI, currentBlock, len(currentBlock))
        else if block==1
            memcopy(blockJ, currentBlock, len(currentBlock))
        else if block==2
            memcopy(blockL, currentBlock, len(currentBlock))
        else if block==3
            memcopy(blockO, currentBlock, len(currentBlock))
        else if block==4
            memcopy(blockS, currentBlock, len(currentBlock))
        else if block==5
            memcopy(blockT, currentBlock, len(currentBlock))
        else if block==6
            memcopy(blockZ, currentBlock, len(currentBlock))
    }

    sub rotateCW() {
        ; rotates the current block clockwise.
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

    sub rotateCCW() {
        ; rotates the current block counterclockwise.
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


    ; For movement checking it is not needed to clamp the x/y coordinates,
    ; because we have to check for brick collisions anyway.
    ; The full play area is bordered by (in)visible characters that will collide.
    ; Collision is determined by reading the screen data directly.
    ; This means the current position of the block on the screen has to be cleared first,
    ; and redrawn after the collision result has been determined.

    sub canRotateCW(ubyte xpos, ubyte ypos) -> ubyte {
        main.drawBlock(xpos, ypos, 32)
        rotateCW()
        ubyte collision = collides(xpos, ypos)
        rotateCCW()
        main.drawBlock(xpos, ypos, 160)
        return not collision
    }

    sub canRotateCCW(ubyte xpos, ubyte ypos) -> ubyte {
        main.drawBlock(xpos, ypos, 32)
        rotateCCW()
        ubyte collision = collides(xpos, ypos)
        rotateCW()
        main.drawBlock(xpos, ypos, 160)
        return not collision
    }

    sub canMoveLeft(ubyte xpos, ubyte ypos) -> ubyte {
        main.drawBlock(xpos, ypos, 32)
        ubyte collision = collides(xpos-1, ypos)
        main.drawBlock(xpos, ypos, 160)
        return not collision
    }

    sub canMoveRight(ubyte xpos, ubyte ypos) -> ubyte {
        main.drawBlock(xpos, ypos, 32)
        ubyte collision = collides(xpos+1, ypos)
        main.drawBlock(xpos, ypos, 160)
        return not collision
    }

    sub canMoveDown(ubyte xpos, ubyte ypos) -> ubyte {
        main.drawBlock(xpos, ypos, 32)
        ubyte collision = collides(xpos, ypos+1)
        main.drawBlock(xpos, ypos, 160)
        return not collision
    }

    sub collides(ubyte xpos, ubyte ypos) -> ubyte {
        return currentBlock[0] and c64scr.getchr(xpos, ypos)!=32
                    or currentBlock[1] and c64scr.getchr(xpos+1, ypos)!=32
                    or currentBlock[2] and c64scr.getchr(xpos+2, ypos)!=32
                    or currentBlock[3] and c64scr.getchr(xpos+3, ypos)!=32
                    or currentBlock[4] and c64scr.getchr(xpos, ypos+1)!=32
                    or currentBlock[5] and c64scr.getchr(xpos+1, ypos+1)!=32
                    or currentBlock[6] and c64scr.getchr(xpos+2, ypos+1)!=32
                    or currentBlock[7] and c64scr.getchr(xpos+3, ypos+1)!=32
                    or currentBlock[8] and c64scr.getchr(xpos, ypos+2)!=32
                    or currentBlock[9] and c64scr.getchr(xpos+1, ypos+2)!=32
                    or currentBlock[10] and c64scr.getchr(xpos+2, ypos+2)!=32
                    or currentBlock[11] and c64scr.getchr(xpos+3, ypos+2)!=32
                    or currentBlock[12] and c64scr.getchr(xpos, ypos+3)!=32
                    or currentBlock[13] and c64scr.getchr(xpos+1, ypos+3)!=32
                    or currentBlock[14] and c64scr.getchr(xpos+2, ypos+3)!=32
                    or currentBlock[15] and c64scr.getchr(xpos+3, ypos+3)!=32
    }

    sub isGameOver(ubyte xpos, ubyte ypos) -> ubyte {
        return ypos==main.startYpos and not canMoveDown(xpos, ypos)
    }
}
