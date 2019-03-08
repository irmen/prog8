
; TehTriz - a Tetris clone.
;

; @todo: holding a block
; @todo: show next 2 blocks instead of just 1
; @todo: simple sound effects?  slight click when moving, swish when rotating/dropping, soft explosion when lines are cleared, buzz at game over


~ main {

    const ubyte boardOffsetX = 14
    const ubyte boardOffsetY = 3
    const ubyte boardWidth = 10
    const ubyte boardHeight = 20
    const ubyte startXpos = boardOffsetX + 3
    const ubyte startYpos = boardOffsetY - 2

    ubyte lines
    uword score
    ubyte xpos
    ubyte ypos
    ubyte nextBlock


    sub start() {
        @(650) = 128        ; set all keys to repeat
        sound.init()
        newGame()
        drawBoard()
        gameOver()

newgame:
        newGame()
        drawBoard()
        spawnNextBlock()

        ubyte joystick_delay=1

waitkey:
        if c64.TIME_LO==30 {
            c64.TIME_LO = 0
            if blocklogic.canMoveDown(xpos, ypos) {

                ; slowly move the block down
                drawBlock(xpos, ypos, 32)
                ypos++
                sound.blockmove()
                drawBlock(xpos, ypos, 160)

            } else {
                ; block can't move further down!
                ; check if the game area is full, if not, spawn the next block at the top.
                if blocklogic.isGameOver(xpos, ypos) {
                    gameOver()
                    goto newgame
                } else {
                    spawnNextBlock()
                }
            }
        }

        ubyte key=c64.GETIN()
        ubyte joystick1 = c64.CIA1PRB
        if key==0 and joystick1==255 goto waitkey

        if joystick1!=255 {
            joystick_delay--
            if_nz goto waitkey
        }

        if key==157 or key==',' or not (joystick1 & 4) {
            ; move left
            if blocklogic.canMoveLeft(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                xpos--
                drawBlock(xpos, ypos, 160)
                sound.blockrotatedrop()
            }
        }
        else if key==29 or key=='.' or not (joystick1 & 8) {
            ; move right
            if blocklogic.canMoveRight(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                xpos++
                drawBlock(xpos, ypos, 160)
                sound.blockrotatedrop()
            }
        }
        else if key==17 or key=='m' or not (joystick1 & 2) {
            ; move down faster
            if blocklogic.canMoveDown(xpos, ypos) {
                drawBlock(xpos, ypos, 32)
                ypos++
                drawBlock(xpos, ypos, 160)
                sound.blockrotatedrop()
            }
        }
        else if key==145 or key==' ' or not (joystick1 & 1) {
            ; drop down immediately
            drawBlock(xpos, ypos, 32)
            ypos = boardOffsetY+boardHeight-4  ; @todo determine proper y position
            drawBlock(xpos, ypos, 160)
            sound.blockrotatedrop()
        }
        else if key=='z' {      ; no joystick equivalent (there is only 1 fire button)
            ; rotate counter clockwise
            drawBlock(xpos, ypos, 32)
            if blocklogic.canRotateCCW(xpos, ypos) {
                blocklogic.rotateCCW()
                sound.blockrotatedrop()
            }
            else if blocklogic.canRotateCCW(xpos-1, ypos) {
                xpos--
                blocklogic.rotateCCW()
                sound.blockrotatedrop()
            }
            else if blocklogic.canRotateCCW(xpos+1, ypos) {
                xpos++
                blocklogic.rotateCCW()
                sound.blockrotatedrop()
            }
            drawBlock(xpos, ypos, 160)
        }
        else if key=='x' or not (joystick1 & 16) {
            ; rotate clockwise
            drawBlock(xpos, ypos, 32)
            if blocklogic.canRotateCW(xpos, ypos) {
                blocklogic.rotateCW()
                sound.blockrotatedrop()
            }
            else if blocklogic.canRotateCW(xpos-1, ypos) {
                xpos--
                blocklogic.rotateCW()
                sound.blockrotatedrop()
            }
            else if blocklogic.canRotateCW(xpos+1, ypos) {
                xpos++
                blocklogic.rotateCW()
                sound.blockrotatedrop()
            }
            drawBlock(xpos, ypos, 160)
        }
        joystick_delay = 140        ; this more or less slows down the joystick movements to the rate of what key repeats do

        ; @todo check if line(s) are full -> flash/clear line(s) + add score + move rest down

        goto waitkey

    }


    sub gameOver() {
        sound.gameover()
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

        c64scr.PLOT(7, 18)
        c64.CHROUT('U')
        c64scr.print("────────────────────────")
        c64.CHROUT('I')
        c64scr.PLOT(7, 19)
        c64scr.print("│    f1 for new game     │")
        c64scr.PLOT(7, 20)
        c64.CHROUT('J')
        c64scr.print("────────────────────────")
        c64.CHROUT('K')

        while(c64.GETIN()!=133) {
            ; endless loop until user presses F1 to restart the game
        }
    }

    sub newGame() {
        lines = 0
        score = 0
        xpos = startXpos
        ypos = startYpos
        nextBlock = rnd() % 7
    }

    sub spawnNextBlock() {
        sound.blockmove()
        c64.TIME_LO = 0
        blocklogic.newCurrentBlock(nextBlock)
        nextBlock = (rnd() + c64.RASTER) % 7
        drawNextBlock()
        xpos = startXpos
        ypos = startYpos
        drawBlock(xpos, ypos, 160)
    }

    sub drawBoard() {
        c64.CLEARSCR()
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
        c64scr.PLOT(28,18)
        c64scr.print("controls:")
        c64.COLOR = 11
        c64scr.PLOT(27,19)
        c64scr.print("z/x  rotate")
        c64scr.PLOT(27,20)
        c64scr.print(",/.  move")
        c64scr.PLOT(27,21)
        c64scr.print("spc  drop")
        c64scr.PLOT(27,22)
        c64scr.print("  m  descend")
        c64scr.PLOT(27,23)
        c64scr.print("or joystick1")

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

        for i in 6 to 0 step -1 {
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
    ubyte[16] blockI = [0,0,0,0,        ; cyan ; note: special rotation (around matrix center)
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
        if currentBlockNum==0 {
            ; the 'I' block rotates a 4x4 matrix around the center
            rotated[0] = currentBlock[12]
            rotated[1] = currentBlock[8]
            rotated[2] = currentBlock[4]
            rotated[3] = currentBlock[0]
            rotated[4] = currentBlock[13]
            rotated[5] = currentBlock[9]
            rotated[6] = currentBlock[5]
            rotated[7] = currentBlock[1]
            rotated[8] = currentBlock[14]
            rotated[9] = currentBlock[10]
            rotated[10] = currentBlock[6]
            rotated[11] = currentBlock[2]
            rotated[12] = currentBlock[15]
            rotated[13] = currentBlock[11]
            rotated[14] = currentBlock[7]
            rotated[15] = currentBlock[3]
        }
        else if currentBlockNum!=3 {
            ; rotate all blocks (except 3, the square) around their center square in a 3x3 matrix
            memset(rotated, len(rotated), 0)
            rotated[0] = currentBlock[8]
            rotated[1] = currentBlock[4]
            rotated[2] = currentBlock[0]
            rotated[4] = currentBlock[9]
            rotated[5] = currentBlock[5]
            rotated[6] = currentBlock[1]
            rotated[8] = currentBlock[10]
            rotated[9] = currentBlock[6]
            rotated[10] = currentBlock[2]
        }

        memcopy(rotated, currentBlock, len(currentBlock))
    }

    sub rotateCCW() {
        ; rotates the current block counterclockwise.
        if currentBlockNum==0 {
            ; the 'I' block rotates a 4x4 matrix around the center
            rotated[0] = currentBlock[3]
            rotated[1] = currentBlock[7]
            rotated[2] = currentBlock[11]
            rotated[3] = currentBlock[15]
            rotated[4] = currentBlock[2]
            rotated[5] = currentBlock[6]
            rotated[6] = currentBlock[10]
            rotated[7] = currentBlock[14]
            rotated[8] = currentBlock[1]
            rotated[9] = currentBlock[5]
            rotated[10] = currentBlock[9]
            rotated[11] = currentBlock[13]
            rotated[12] = currentBlock[0]
            rotated[13] = currentBlock[4]
            rotated[14] = currentBlock[8]
            rotated[15] = currentBlock[12]
        }
        else if currentBlockNum!=3 {
            ; rotate all blocks (except 3, the square) around their center square in a 3x3 matrix
            memset(rotated, len(rotated), 0)
            rotated[0] = currentBlock[2]
            rotated[1] = currentBlock[6]
            rotated[2] = currentBlock[10]
            rotated[4] = currentBlock[1]
            rotated[5] = currentBlock[5]
            rotated[6] = currentBlock[9]
            rotated[8] = currentBlock[0]
            rotated[9] = currentBlock[4]
            rotated[10] = currentBlock[8]
        }
        memcopy(rotated, currentBlock, len(currentBlock))
    }

    ; For movement checking it is not needed to clamp the x/y coordinates,
    ; because we have to check for brick collisions anyway.
    ; The full play area is bordered by (in)visible characters that will collide.
    ; Collision is determined by reading the screen data directly.

    sub canRotateCW(ubyte xpos, ubyte ypos) -> ubyte {
        rotateCW()
        ubyte collision = collides(xpos, ypos)
        rotateCCW()
        return not collision
    }

    sub canRotateCCW(ubyte xpos, ubyte ypos) -> ubyte {
        rotateCCW()
        ubyte collision = collides(xpos, ypos)
        rotateCW()
        return not collision
    }

    sub canMoveLeft(ubyte xpos, ubyte ypos) -> ubyte {
        main.drawBlock(xpos, ypos, 32)      ; @todo do this in main itself?
        ubyte collision = collides(xpos-1, ypos)
        main.drawBlock(xpos, ypos, 160)
        return not collision
    }

    sub canMoveRight(ubyte xpos, ubyte ypos) -> ubyte {
        main.drawBlock(xpos, ypos, 32); @todo do this in main itself?
        ubyte collision = collides(xpos+1, ypos)
        main.drawBlock(xpos, ypos, 160)
        return not collision
    }

    sub canMoveDown(ubyte xpos, ubyte ypos) -> ubyte {
        main.drawBlock(xpos, ypos, 32); @todo do this in main itself?
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


~ sound {

    sub init() {
        ; todo
    }

    sub blockmove() {
        ; todo soft click
    }

    sub blockrotatedrop() {
        ; todo swish
    }

    sub lineclear() {
        ; todo explosion like
    }

    sub gameover() {
        ; todo buzz?
    }
}
