; TehTriz - a Tetris clone.
;
; features:
;   holding area
;   wall kick rotations
;   shows next piece
;   staged speed increase
;   some simple sound effects
;
; @todo show ghost?


~ main {

    const ubyte boardOffsetX = 14
    const ubyte boardOffsetY = 3
    const ubyte boardWidth = 10
    const ubyte boardHeight = 20
    const ubyte startXpos = boardOffsetX + 3
    const ubyte startYpos = boardOffsetY - 2
    uword lines
    uword score
    ubyte xpos
    ubyte ypos
    ubyte nextBlock
    ubyte speedlevel
    ubyte holding
    ubyte holdingAllowed


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

waitkey:
        if c64.TIME_LO>=(60-4*speedlevel) {
            c64.TIME_LO = 0

            drawBlock(xpos, ypos, 32) ; hide block
            if blocklogic.noCollision(xpos, ypos+1) {
                ; slowly move the block down
                ypos++
                drawBlock(xpos, ypos, 160)  ; show block on new position
            } else {
                ; block can't move further down!
                ; check if the game area is full, if not, spawn the next block at the top.
                if blocklogic.isGameOver(xpos, ypos) {
                    gameOver()
                    goto newgame
                } else {
                    sound.blockrotate()
                    checkForLines()
                    spawnNextBlock()
                    score++
                }
            }

            drawScore()
        }

        ubyte key=c64.GETIN()
        if key==0 goto waitkey

        keypress(key)

        goto waitkey

    }

    sub keypress(ubyte key) {
        if key==157 or key==',' {
            ; move left
            drawBlock(xpos, ypos, 32)
            if blocklogic.noCollision(xpos-1, ypos) {
                xpos--
            }
            drawBlock(xpos, ypos, 160)
        }
        else if key==29 or key=='/' {
            ; move right
            drawBlock(xpos, ypos, 32)
            if blocklogic.noCollision(xpos+1, ypos) {
                xpos++
            }
            drawBlock(xpos, ypos, 160)
        }
        else if key==17 or key=='.' {
            ; move down faster
            drawBlock(xpos, ypos, 32)
            if blocklogic.noCollision(xpos, ypos+1) {
                ypos++
            }
            drawBlock(xpos, ypos, 160)
        }
        else if key==145 or key==' ' {
            ; drop down immediately
            drawBlock(xpos, ypos, 32)
            ubyte dropypos
            for dropypos in ypos+1 to boardOffsetY+boardHeight-1 {
                if not blocklogic.noCollision(xpos, dropypos) {
                    dropypos--   ; the furthest down that still fits
                    break
                }
            }
            if dropypos>ypos {
                ypos = dropypos
                sound.blockdrop()
                drawBlock(xpos, ypos, 160)
                checkForLines()
                spawnNextBlock()
                score++
                drawScore()
            }
        }
        else if key=='z' {      ; no joystick equivalent (there is only 1 fire button)
            ; rotate counter clockwise
            drawBlock(xpos, ypos, 32)
            if blocklogic.canRotateCCW(xpos, ypos) {
                blocklogic.rotateCCW()
                sound.blockrotate()
            }
            else if blocklogic.canRotateCCW(xpos-1, ypos) {
                xpos--
                blocklogic.rotateCCW()
                sound.blockrotate()
            }
            else if blocklogic.canRotateCCW(xpos+1, ypos) {
                xpos++
                blocklogic.rotateCCW()
                sound.blockrotate()
            }
            drawBlock(xpos, ypos, 160)
        }
        else if key=='x' {
            ; rotate clockwise
            drawBlock(xpos, ypos, 32)
            if blocklogic.canRotateCW(xpos, ypos) {
                blocklogic.rotateCW()
                sound.blockrotate()
            }
            else if blocklogic.canRotateCW(xpos-1, ypos) {
                xpos--
                blocklogic.rotateCW()
                sound.blockrotate()
            }
            else if blocklogic.canRotateCW(xpos+1, ypos) {
                xpos++
                blocklogic.rotateCW()
                sound.blockrotate()
            }
            drawBlock(xpos, ypos, 160)
        }
        else if key=='c' {
            ; hold
            if holdingAllowed {
                sound.swapping()
                if holding<7 {
                    drawBlock(xpos, ypos, 32)
                    ubyte newholding = blocklogic.currentBlockNum
                    swapBlock(holding)
                    holding = newholding
                    holdingAllowed = false
                } else {
                    holding = blocklogic.currentBlockNum
                    drawBlock(xpos, ypos, 32)
                    spawnNextBlock()
                }
                drawHoldBlock()
            }
        }
    }

    sub checkForLines() {
        ; check if line(s) are full -> flash/clear line(s) + add score + move rest down
        ubyte[boardHeight] complete_lines
        ubyte num_lines=0
        memset(complete_lines, len(complete_lines), 0)
        for ubyte linepos in boardOffsetY to boardOffsetY+boardHeight-1 {
            if blocklogic.isLineFull(linepos) {
                complete_lines[num_lines]=linepos
                num_lines++
                for ubyte x in boardOffsetX to boardOffsetX+boardWidth-1
                    c64scr.setcc(x, linepos, 160, 1)
            }
        }
        if num_lines {
            if num_lines>3
                sound.lineclear_big()
            else
                sound.lineclear()
            c64.TIME_LO=0
            while c64.TIME_LO<20 {
                ; slight delay to flash the line
            }
            c64.TIME_LO=0
            for ubyte linepos in complete_lines
                if linepos and blocklogic.isLineFull(linepos)
                    blocklogic.collapse(linepos)
            lines += num_lines
            uword[4] scores = [10, 25, 50, 100]      ; can never clear more than 4 lines
            score += scores[num_lines-1]
            speedlevel = 1+lsb(lines/10)
            drawScore()
        }
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
        speedlevel = 1
        nextBlock = rnd() % 7
        holding = 255
        holdingAllowed = true
    }

    sub swapBlock(ubyte newblock) {
        c64.TIME_LO = 0
        blocklogic.newCurrentBlock(newblock)
        xpos = startXpos
        ypos = startYpos
        drawBlock(xpos, ypos, 160)
    }

    sub spawnNextBlock() {
        swapBlock(nextBlock)
        nextBlock = (rnd() + c64.RASTER) % 7
        drawNextBlock()
        holdingAllowed = true
    }

    sub drawBoard() {
        c64.CLEARSCR()
        c64.COLOR = 7
        c64scr.PLOT(1,1)
        c64scr.print("irmen's")
        c64scr.PLOT(2,2)
        c64scr.print("teh▁triz")
        c64.COLOR = 5
        c64scr.PLOT(6,4)
        c64scr.print("hold:")
        c64scr.PLOT(2,22)
        c64scr.print("speed: ")
        c64scr.PLOT(28,3)
        c64scr.print("next:")
        c64scr.PLOT(28,10)
        c64scr.print("lines:")
        c64scr.PLOT(28,14)
        c64scr.print("score:")
        c64.COLOR = 12
        c64scr.PLOT(27,18)
        c64scr.print("controls:")
        c64.COLOR = 11
        c64scr.PLOT(28,19)
        c64scr.print(",/  move")
        c64scr.PLOT(28,20)
        c64scr.print("zx  rotate")
        c64scr.PLOT(29,21)
        c64scr.print(".  descend")
        c64scr.PLOT(27,22)
        c64scr.print("spc  drop")
        c64scr.PLOT(29,23)
        c64scr.print("c  hold")

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

        ubyte[5] colors = [6,8,7,5,4]
        for i in len(colors)-1 to 0 step -1 {
            for ubyte x in 5 to 0 step -1 {
                c64scr.setcc(6+x-i, 11+2*i, 102, colors[i])
            }
        }
        drawScore()
    }

    sub drawScore() {
        c64.COLOR=1
        c64scr.PLOT(30,11)
        c64scr.print_uw(lines)
        c64scr.PLOT(30,15)
        c64scr.print_uw(score)
        c64scr.PLOT(9,22)
        c64scr.print_ub(speedlevel)
    }

    sub drawNextBlock() {
        const ubyte nextBlockXpos = 29
        const ubyte nextBlockYpos = 5
        for ubyte x in nextBlockXpos+3 to nextBlockXpos step -1 {
            c64scr.setcc(x, nextBlockYpos, ' ', 0)
            c64scr.setcc(x, nextBlockYpos+1, ' ', 0)
        }

        ; reuse the normal block draw routine (because we can't manipulate array pointers yet)
        ubyte prev = blocklogic.currentBlockNum
        blocklogic.newCurrentBlock(nextBlock)
        drawBlock(nextBlockXpos, nextBlockYpos, 160)
        blocklogic.newCurrentBlock(prev)
    }

    sub drawHoldBlock() {
        const ubyte holdBlockXpos = 7
        const ubyte holdBlockYpos = 6
        for ubyte x in holdBlockXpos+3 to holdBlockXpos step -1 {
            c64scr.setcc(x, holdBlockYpos, '@', 0)
            c64scr.setcc(x, holdBlockYpos+1, '@', 0)
        }
        if holding < 7 {
            ; reuse the normal block draw routine (because we can't manipulate array pointers yet)
            ubyte prev = blocklogic.currentBlockNum
            blocklogic.newCurrentBlock(holding)
            drawBlock(holdBlockXpos, holdBlockYpos, 160)
            blocklogic.newCurrentBlock(prev)
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
            memcopy(rotated, currentBlock, len(currentBlock))
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
            memcopy(rotated, currentBlock, len(currentBlock))
        }
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
            memcopy(rotated, currentBlock, len(currentBlock))
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
            memcopy(rotated, currentBlock, len(currentBlock))
        }
    }

    ; For movement checking it is not needed to clamp the x/y coordinates,
    ; because we have to check for brick collisions anyway.
    ; The full play area is bordered by (in)visible characters that will collide.
    ; Collision is determined by reading the screen data directly.

    sub canRotateCW(ubyte xpos, ubyte ypos) -> ubyte {
        rotateCW()
        ubyte nocollision = noCollision(xpos, ypos)
        rotateCCW()
        return nocollision
    }

    sub canRotateCCW(ubyte xpos, ubyte ypos) -> ubyte {
        rotateCCW()
        ubyte nocollision = noCollision(xpos, ypos)
        rotateCW()
        return nocollision
    }

    sub noCollision(ubyte xpos, ubyte ypos) -> ubyte {
        for ubyte i in 15 to 0 step -1 {
            if currentBlock[i] and c64scr.getchr(xpos + (i&3), ypos+i/4)!=32
                return false
        }
        return true
    }

    sub isGameOver(ubyte xpos, ubyte ypos) -> ubyte {
        main.drawBlock(xpos, ypos, 32)
        ubyte result = ypos==main.startYpos and not noCollision(xpos, ypos+1)
        main.drawBlock(xpos, ypos, 160)
        return result
    }

    sub isLineFull(ubyte ypos) -> ubyte {
        for ubyte x in main.boardOffsetX to main.boardOffsetX+main.boardWidth-1 {
            if c64scr.getchr(x, ypos)==32
                return false
        }
        return true
    }

    sub collapse(ubyte ypos) {
        while(ypos>main.startYpos+1) {
            for ubyte x in main.boardOffsetX+main.boardWidth-1 to main.boardOffsetX step -1 {
                ubyte char = c64scr.getchr(x, ypos-1)
                ubyte color = c64scr.getclr(x, ypos-1)
                c64scr.setcc(x, ypos, char, color)
            }
            ypos--
        }
    }
}


~ sound {

    sub init() {
        c64.MVOL = 15
    }

    sub blockrotate() {
        ; soft click
        c64.MVOL = 5
        c64.AD1 = %00100010
        c64.SR1 = %00000000
        c64.FREQ1 = 15600
        c64.CR1 = %10000000
        c64.CR1 = %10000001
    }

    sub blockdrop() {
        ; swish
        c64.MVOL = 5
        c64.AD1 = %01010111
        c64.SR1 = %00000000
        c64.FREQ1 = 4600
        c64.CR1 = %10000000
        c64.CR1 = %10000001
    }

    sub swapping() {
        ; beep
        c64.MVOL = 8
        c64.AD1 = %01010111
        c64.SR1 = %00000000
        c64.FREQ1 = 5500
        c64.CR1 = %00010000
        c64.CR1 = %00010001
    }

    sub lineclear() {
        ; explosion
        c64.MVOL = 15
        c64.AD1 = %01100110
        c64.SR1 = %00000000
        c64.FREQ1 = 1600
        c64.CR1 = %10000000
        c64.CR1 = %10000001
    }

    sub lineclear_big() {
        ; big explosion
        c64.MVOL = 15
        c64.AD1 = %01101010
        c64.SR1 = %00000000
        c64.FREQ1 = 2600
        c64.CR1 = %10000000
        c64.CR1 = %10000001
    }

    sub gameover() {
        ; buzz
        c64.MVOL = 15
        c64.FREQ2 = 600
        c64.AD2 = %00111010
        c64.SR2 = %00000000
        c64.CR2 = %00110000
        c64.CR2 = %00110001
    }
}
