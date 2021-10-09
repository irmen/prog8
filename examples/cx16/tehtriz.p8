; TehTriz - a Tetris clone.  Commander X16 version.
;
; features:
;   holding area
;   wall kick rotations
;   shows next piece
;   staged speed increase
;   simplistic sound effects (Vera PSG)


%import syslib
%import textio
%import test_stack


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
        void cx16.screen_set_mode(0)       ; low res
        sound.init()
        newGame()
        drawBoard()
        gameOver()

newgame:
        newGame()
        drawBoard()
        spawnNextBlock()

waitkey:
        ubyte time_lo = lsb(c64.RDTIM16())
        if time_lo>=(60-4*speedlevel) {
            c64.SETTIM(0,0,0)

            drawBlock(xpos, ypos, true) ; hide block
            if blocklogic.noCollision(xpos, ypos+1) {
                ; slowly move the block down
                ypos++
                drawBlock(xpos, ypos, false)  ; show block on new position
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

            ; txt.plot(0,0)
            ; test_stack.test()
        }

        ubyte key=c64.GETIN()
        if key==0 goto waitkey

        keypress(key)

        goto waitkey

    }

    sub move_left() {
        drawBlock(xpos, ypos, true)
        if blocklogic.noCollision(xpos-1, ypos) {
            xpos--
        }
        drawBlock(xpos, ypos, false)
    }

    sub move_right() {
        drawBlock(xpos, ypos, true)
        if blocklogic.noCollision(xpos+1, ypos) {
            xpos++
        }
        drawBlock(xpos, ypos, false)
    }

    sub move_down_faster() {
        drawBlock(xpos, ypos, true)
        if blocklogic.noCollision(xpos, ypos+1) {
            ypos++
        }
        drawBlock(xpos, ypos, false)
    }

    sub drop_down_immediately() {
        drawBlock(xpos, ypos, true)
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
            drawBlock(xpos, ypos, false)
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
                drawBlock(xpos, ypos, true)
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
                drawBlock(xpos, ypos, false)
            }
            'x' -> {
                ; rotate clockwise
                drawBlock(xpos, ypos, true)
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
                drawBlock(xpos, ypos, false)
            }
            'c' -> {
                ; hold
                if holdingAllowed {
                    sound.swapping()
                    if holding<7 {
                        drawBlock(xpos, ypos, true)
                        ubyte newholding = blocklogic.currentBlockNum
                        swapBlock(holding)
                        holding = newholding
                        holdingAllowed = false
                    } else {
                        holding = blocklogic.currentBlockNum
                        drawBlock(xpos, ypos, true)
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
        sys.memset(complete_lines, len(complete_lines), 0)
        for linepos in boardOffsetY to boardOffsetY+boardHeight-1 {
            if blocklogic.isLineFull(linepos) {
                complete_lines[num_lines]=linepos
                num_lines++
                ubyte x
                for x in boardOffsetX to boardOffsetX+boardWidth-1
                    txt.setcc2(x, linepos, 160, 1)
            }
        }
        if num_lines {
            if num_lines>3
                sound.lineclear_big()
            else
                sound.lineclear()
            sys.wait(10) ; slight delay to flash the line
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
        blocklogic.newCurrentBlock(newblock)
        xpos = startXpos
        ypos = startYpos
        drawBlock(xpos, ypos, false)
    }

    sub spawnNextBlock() {
        swapBlock(nextBlock)
        nextBlock = (rnd() + lsb(c64.RDTIM16())) % 7
        drawNextBlock()
        holdingAllowed = true
    }

    sub drawBoard() {
        txt.clear_screen()
        txt.color(7)
        txt.plot(1,1)
        txt.print("* tehtriz *")
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
            txt.setcc2(x, nextBlockYpos, ' ', 0)
            txt.setcc2(x, nextBlockYpos+1, ' ', 0)
        }

        ; reuse the normal block draw routine (because we can't manipulate array pointers yet)
        ubyte prev = blocklogic.currentBlockNum
        blocklogic.newCurrentBlock(nextBlock)
        drawBlock(nextBlockXpos, nextBlockYpos, false)
        blocklogic.newCurrentBlock(prev)
    }

    sub drawHoldBlock() {
        const ubyte holdBlockXpos = 7
        const ubyte holdBlockYpos = 6
        ubyte x
        for x in holdBlockXpos+3 downto holdBlockXpos {
            txt.setcc2(x, holdBlockYpos, '@', 0)
            txt.setcc2(x, holdBlockYpos+1, '@', 0)
        }
        if holding < 7 {
            ; reuse the normal block draw routine (because we can't manipulate array pointers yet)
            ubyte prev = blocklogic.currentBlockNum
            blocklogic.newCurrentBlock(holding)
            drawBlock(holdBlockXpos, holdBlockYpos, false)
            blocklogic.newCurrentBlock(prev)
        }
    }

    sub drawBlock(ubyte x, ubyte y, ubyte erase) {
        ubyte char = 79   ; top left edge
        if erase
            char = 32   ; space
        ubyte @zp i
        for i in 15 downto 0 {
            ubyte @zp c=blocklogic.currentBlock[i]
            if c {
                if erase
                    c=0
                else {
                    ubyte edge = blocklogic.edgecolors[c]
                    c <<= 4
                    c |= edge
                }
                txt.setcc2((i&3)+x, (i/4)+y, char, c)
            }
        }
    }
}


blocklogic {

    ubyte currentBlockNum
    ubyte[16] currentBlock
    ubyte[16] rotated

    ; the 7 tetrominos
    ubyte[] blockI = [0,0,0,0,        ; blue    note: special rotation (around matrix center)
                      6,6,6,6,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockJ = [5,0,0,0,        ; green
                      5,5,5,0,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockL = [0,0,2,0,        ; red
                      2,2,2,0,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockO = [0,12,12,0,      ; grey ; note: no rotation (square)
                      0,12,12,0,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockS = [0,11,11,0,        ; dark grey
                      11,11,0,0,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockT = [0,9,0,0,        ; brown
                      9,9,9,0,
                      0,0,0,0,
                      0,0,0,0]
    ubyte[] blockZ = [4,4,0,0,        ; purple
                      0,4,4,0,
                      0,0,0,0,
                      0,0,0,0]

    ubyte[16] edgecolors = [11, 1, 10, 0, 10, 13, 14, 1, 7, 7, 11, 12, 15, 1, 1, 1]      ; highlighed colors for the edges

    uword[] blocks = [&blockI, &blockJ, &blockL, &blockO, &blockS, &blockT, &blockZ]

    sub newCurrentBlock(ubyte block) {
        currentBlockNum = block
        sys.memcopy(blocks[block], currentBlock, len(currentBlock))
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
            sys.memcopy(rotated, currentBlock, len(currentBlock))
        }
        else if currentBlockNum!=3 {
            ; rotate all blocks (except 3, the square) around their center square in a 3x3 matrix
            sys.memset(rotated, len(rotated), 0)
            rotated[0] = currentBlock[8]
            rotated[1] = currentBlock[4]
            rotated[2] = currentBlock[0]
            rotated[4] = currentBlock[9]
            rotated[5] = currentBlock[5]
            rotated[6] = currentBlock[1]
            rotated[8] = currentBlock[10]
            rotated[9] = currentBlock[6]
            rotated[10] = currentBlock[2]
            sys.memcopy(rotated, currentBlock, len(currentBlock))
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
            sys.memcopy(rotated, currentBlock, len(currentBlock))
        }
        else if currentBlockNum!=3 {
            ; rotate all blocks (except 3, the square) around their center square in a 3x3 matrix
            sys.memset(rotated, len(rotated), 0)
            rotated[0] = currentBlock[2]
            rotated[1] = currentBlock[6]
            rotated[2] = currentBlock[10]
            rotated[4] = currentBlock[1]
            rotated[5] = currentBlock[5]
            rotated[6] = currentBlock[9]
            rotated[8] = currentBlock[0]
            rotated[9] = currentBlock[4]
            rotated[10] = currentBlock[8]
            sys.memcopy(rotated, currentBlock, len(currentBlock))
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
        main.drawBlock(xpos, ypos, true)
        ubyte result = ypos==main.startYpos and not noCollision(xpos, ypos+1)
        main.drawBlock(xpos, ypos, false)
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
                txt.setcc2(x, ypos, char, color)
            }
            ypos--
        }
    }
}


sound {
    sub init() {
        cx16.vpoke(1, $f9c2, %00111111)     ; volume max, no channels
    }

    sub blockrotate() {
        ; soft click/"tschk" sound
        uword freq = 15600
        cx16.vpoke(1, $f9c0, lsb(freq))
        cx16.vpoke(1, $f9c1, msb(freq))
        cx16.vpoke(1, $f9c2, %11110000)     ; half volume
        cx16.vpoke(1, $f9c3, %11000000)     ; noise waveform
        sys.wait(2)
        cx16.vpoke(1, $f9c2, 0)     ; shut off
    }

    sub blockdrop() {
        ; swish
        uword freq = 4600
        cx16.vpoke(1, $f9c0, lsb(freq))
        cx16.vpoke(1, $f9c1, msb(freq))
        cx16.vpoke(1, $f9c2, %11110000)     ; half volume
        cx16.vpoke(1, $f9c3, %11000000)     ; noise waveform
        sys.wait(6)
        cx16.vpoke(1, $f9c2, 0)     ; shut off
    }

    sub swapping() {
        ; beep
        uword freq = 1500
        cx16.vpoke(1, $f9c0, lsb(freq))
        cx16.vpoke(1, $f9c1, msb(freq))
        cx16.vpoke(1, $f9c2, %11110000)     ; half volume
        cx16.vpoke(1, $f9c3, %10000000)     ; triangle waveform
        sys.wait(6)
        cx16.vpoke(1, $f9c2, 0)     ; shut off
    }

    sub lineclear() {
        ; explosion
        uword freq = 1400
        cx16.vpoke(1, $f9c0, lsb(freq))
        cx16.vpoke(1, $f9c1, msb(freq))
        cx16.vpoke(1, $f9c2, %11111111)     ; max volume
        cx16.vpoke(1, $f9c3, %11000000)     ; noise waveform
        sys.wait(8)
        cx16.vpoke(1, $f9c2, 0)     ; shut off
    }

    sub lineclear_big() {
        ; big explosion
        uword freq = 2500
        cx16.vpoke(1, $f9c0, lsb(freq))
        cx16.vpoke(1, $f9c1, msb(freq))
        cx16.vpoke(1, $f9c2, %11111111)     ; max volume
        cx16.vpoke(1, $f9c3, %11000000)     ; noise waveform
        sys.wait(30)
        cx16.vpoke(1, $f9c2, 0)     ; shut off
    }

    sub gameover() {
        ; attempt at buzz/boing (but can't get the sawtooth/triangle combined waveform of the sid)
        uword freq = 200
        cx16.vpoke(1, $f9c0, lsb(freq))
        cx16.vpoke(1, $f9c1, msb(freq))
        cx16.vpoke(1, $f9c2, %11111111)     ; max volume
        cx16.vpoke(1, $f9c3, %01000000)     ; sawtooth waveform
        sys.wait(30)
        cx16.vpoke(1, $f9c2, 0)     ; shut off
    }
}
