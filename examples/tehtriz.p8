; TehTriz - a Tetris clone.
;
; features:
;   holding area
;   wall kick rotations
;   shows next piece
;   staged speed increase
;   some simple sound effects

%target c64
%import syslib
%import textio


main {

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
        c64.disable_runstop_and_charsetswitch()
        ;@(650) = 128        ; set all keys to repeat
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

    sub move_left() {
        drawBlock(xpos, ypos, 32)
        if blocklogic.noCollision(xpos-1, ypos) {
            xpos--
        }
        drawBlock(xpos, ypos, 160)
    }

    sub move_right() {
        drawBlock(xpos, ypos, 32)
        if blocklogic.noCollision(xpos+1, ypos) {
            xpos++
        }
        drawBlock(xpos, ypos, 160)
    }

    sub move_down_faster() {
        drawBlock(xpos, ypos, 32)
        if blocklogic.noCollision(xpos, ypos+1) {
            ypos++
        }
        drawBlock(xpos, ypos, 160)
    }

    sub drop_down_immediately() {
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

    sub keypress(ubyte key) {
        when key {
            157, ',' -> move_left()
            29, '/'  -> move_right()
            17, '.'  -> move_down_faster()
            145, ' ' -> drop_down_immediately()
            'z' -> {
                ; no joystick equivalent (there is only 1 fire button)
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
            'x' -> {
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
            'c' -> {
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
    }

    sub checkForLines() {
        ; check if line(s) are full -> flash/clear line(s) + add score + move rest down
        ubyte[boardHeight] complete_lines
        ubyte num_lines=0
        ubyte linepos
        memset(complete_lines, len(complete_lines), 0)
        for linepos in boardOffsetY to boardOffsetY+boardHeight-1 {
            if blocklogic.isLineFull(linepos) {
                complete_lines[num_lines]=linepos
                num_lines++
                ubyte x
                for x in boardOffsetX to boardOffsetX+boardWidth-1
                    txt.setcc(x, linepos, 160, 1)
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
            for linepos in complete_lines
                if linepos and blocklogic.isLineFull(linepos)
                    blocklogic.collapse(linepos)
            lines += num_lines
            uword[] scores = [10, 25, 50, 100]      ; can never clear more than 4 lines at once
            score += scores[num_lines-1]
            speedlevel = 1+lsb(lines/10)
            drawScore()
        }
    }

    sub gameOver() {
        sound.gameover()
        txt.plot(7, 7)
        c64.CHROUT('U')
        txt.print("────────────────────────")
        c64.CHROUT('I')
        txt.plot(7, 8)
        txt.print("│*** g a m e  o v e r ***│")
        txt.plot(7, 9)
        c64.CHROUT('J')
        txt.print("────────────────────────")
        c64.CHROUT('K')

        txt.plot(7, 18)
        c64.CHROUT('U')
        txt.print("────────────────────────")
        c64.CHROUT('I')
        txt.plot(7, 19)
        txt.print("│    f1 for new game     │")
        txt.plot(7, 20)
        c64.CHROUT('J')
        txt.print("────────────────────────")
        c64.CHROUT('K')

        ubyte key = 0
        while key!=133 {
            ; endless loop until user presses F1 to restart the game
            key = c64.GETIN()
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
        txt.color(7)
        txt.plot(1,1)
        txt.print("irmen's")
        txt.plot(2,2)
        txt.print("teh▁triz")
        txt.color(5)
        txt.plot(6,4)
        txt.print("hold:")
        txt.plot(2,22)
        txt.print("speed: ")
        txt.plot(28,3)
        txt.print("next:")
        txt.plot(28,10)
        txt.print("lines:")
        txt.plot(28,14)
        txt.print("score:")
        txt.color(12)
        txt.plot(27,18)
        txt.print("controls:")
        txt.color(11)
        txt.plot(28,19)
        txt.print(",/  move")
        txt.plot(28,20)
        txt.print("zx  rotate")
        txt.plot(29,21)
        txt.print(".  descend")
        txt.plot(27,22)
        txt.print("spc  drop")
        txt.plot(29,23)
        txt.print("c  hold")

        txt.setcc(boardOffsetX-1, boardOffsetY-2, 255, 0)           ; invisible barrier
        txt.setcc(boardOffsetX-1, boardOffsetY-3, 255, 0)           ; invisible barrier
        txt.setcc(boardOffsetX+boardWidth, boardOffsetY-2, 255, 0)  ; invisible barrier
        txt.setcc(boardOffsetX+boardWidth, boardOffsetY-3, 255, 0)  ; invisible barrier

        txt.setcc(boardOffsetX-1, boardOffsetY-1, 108, 12)
        txt.setcc(boardOffsetX+boardWidth, boardOffsetY-1, 123, 12)
        txt.setcc(boardOffsetX+boardWidth, boardOffsetY-1, 123, 12)
        txt.setcc(boardOffsetX-1, boardOffsetY+boardHeight, 124, 12)
        txt.setcc(boardOffsetX+boardWidth, boardOffsetY+boardHeight, 126, 12)
        ubyte i
        for i in boardOffsetX+boardWidth-1 downto boardOffsetX {
            txt.setcc(i, boardOffsetY-3, 255, 0)           ; invisible barrier
            txt.setcc(i, boardOffsetY+boardHeight, 69, 11)
        }
        for i in boardOffsetY+boardHeight-1 downto boardOffsetY {
            txt.setcc(boardOffsetX-1, i, 89, 11)
            txt.setcc(boardOffsetX+boardWidth, i, 84, 11)
        }

        ubyte[] colors = [6,8,7,5,4]
        for i in len(colors)-1 downto 0 {
            ubyte x
            for x in 5 downto 0 {
                txt.setcc(6+x-i, 11+2*i, 102, colors[i])
            }
        }
        drawScore()
    }

    sub drawScore() {
        txt.color(1)
        txt.plot(30,11)
        txt.print_uw(lines)
        txt.plot(30,15)
        txt.print_uw(score)
        txt.plot(9,22)
        txt.print_ub(speedlevel)
    }

    sub drawNextBlock() {
        const ubyte nextBlockXpos = 29
        const ubyte nextBlockYpos = 5
        ubyte x
        for x in nextBlockXpos+3 downto nextBlockXpos {
            txt.setcc(x, nextBlockYpos, ' ', 0)
            txt.setcc(x, nextBlockYpos+1, ' ', 0)
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
        ubyte x
        for x in holdBlockXpos+3 downto holdBlockXpos {
            txt.setcc(x, holdBlockYpos, '@', 0)
            txt.setcc(x, holdBlockYpos+1, '@', 0)
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
        ubyte @zp i
        for i in 15 downto 0 {
            ubyte @zp c=blocklogic.currentBlock[i]
            if c
                txt.setcc((i&3)+x, (i/4)+y, character, c)
        }
    }
}


blocklogic {

    ubyte currentBlockNum
    ubyte[16] currentBlock
    ubyte[16] rotated

    ; the 7 tetrominos
    ubyte[] blockI = [0,0,0,0,        ; cyan ; note: special rotation (around matrix center)
                      3,3,3,3,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockJ = [6,0,0,0,        ; blue
                      6,6,6,0,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockL = [0,0,8,0,        ; orange
                      8,8,8,0,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockO = [0,7,7,0,        ; yellow ; note: no rotation (square)
                      0,7,7,0,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockS = [0,5,5,0,        ; green
                      5,5,0,0,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockT = [0,4,0,0,        ; purple
                      4,4,4,0,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockZ = [2,2,0,0,        ; red
                      0,2,2,0,
                      0,0,0,0,
                      0,0,0,0]

    uword[] blocks = [&blockI, &blockJ, &blockL, &blockO, &blockS, &blockT, &blockZ]

    sub newCurrentBlock(ubyte block) {
        currentBlockNum = block
        memcopy(blocks[block], currentBlock, len(currentBlock))
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
        ubyte @zp i
        for i in 15 downto 0 {
            if currentBlock[i] and txt.getchr(xpos + (i&3), ypos+i/4)!=32
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
        ubyte x
        for x in main.boardOffsetX to main.boardOffsetX+main.boardWidth-1 {
            if txt.getchr(x, ypos)==32
                return false
        }
        return true
    }

    sub collapse(ubyte ypos) {
        while ypos>main.startYpos+1 {
            ubyte x
            for x in main.boardOffsetX+main.boardWidth-1 downto main.boardOffsetX {
                ubyte char = txt.getchr(x, ypos-1)
                ubyte color = txt.getclr(x, ypos-1)
                txt.setcc(x, ypos, char, color)
            }
            ypos--
        }
    }
}


sound {

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
