; TehTriz - a Tetris clone.  Commander X16 version.
;
; features:
;   holding area
;   wall kick rotations
;   shows next piece
;   staged speed increase
;   simple sound effects (Vera PSG)

%import syslib
%import textio
%import math
%import psg2

main {

    const ubyte boardOffsetX = 14
    const ubyte boardOffsetY = 5
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
    bool holdingAllowed
    ubyte ticks_since_previous_action
    ubyte ticks_since_previous_move

    sub start() {
        cx16.set_screen_mode(3)
        txt.color2(7,0)                     ; make sure correct screen colors are (re)set
        txt.clear_screen()

        ; gimmick: make a mirrored R
        cx16.vpoke(1,$f000+sc:'r'*8+0, %00111110)
        cx16.vpoke(1,$f000+sc:'r'*8+1, %01100110)
        cx16.vpoke(1,$f000+sc:'r'*8+2, %01100110)
        cx16.vpoke(1,$f000+sc:'r'*8+3, %00111110)
        cx16.vpoke(1,$f000+sc:'r'*8+4, %00011110)
        cx16.vpoke(1,$f000+sc:'r'*8+5, %00110110)
        cx16.vpoke(1,$f000+sc:'r'*8+6, %01100110)
        cx16.vpoke(1,$f000+sc:'r'*8+7, %00000000)

        sound.init()
        newGame()
        drawBoard()
        gameOver()

newgame:
        newGame()
        drawBoard()
        spawnNextBlock()

waitkey:
        sys.waitvsync()
        ticks_since_previous_action++
        ticks_since_previous_move++
        if ticks_since_previous_action==0
            ticks_since_previous_action=255
        if ticks_since_previous_move==0
            ticks_since_previous_move=255

        ubyte time_lo = lsb(cbm.RDTIM16())
        if time_lo>=(60-4*speedlevel) {
            cbm.SETTIM(0,0,0)

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
        }

        ubyte key
        void, key=cbm.GETIN()
        keypress(key)
        cx16.r0,void = cx16.joystick_get(1)
        joystick(cx16.r0)

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

    sub rotate_counterclockwise() {
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

    sub rotate_clockwise() {
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

    sub hold_block() {
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

    sub keypress(ubyte key) {
        when key {
            157, ',' -> move_left()
            29, '/'  -> move_right()
            17, '.'  -> move_down_faster()
            145, ' ' -> drop_down_immediately()
            'z' -> rotate_counterclockwise()
            'x' -> rotate_clockwise()
            'c' -> hold_block()
        }
    }

    sub joystick(uword joy) {
        ; note: we don't process simultaneous button presses
        when joy {
            %1111111111111101 -> {
                if ticks_since_previous_move > 5 {
                    move_left()
                    ticks_since_previous_move = 0
                    ticks_since_previous_action = 0
                }
            }
            %1111111111111110 -> {
                if ticks_since_previous_move > 5 {
                    move_right()
                    ticks_since_previous_move = 0
                    ticks_since_previous_action = 0
                }
            }
            %1111111111111011 -> {
                if ticks_since_previous_move > 5 {
                    move_down_faster()
                    ticks_since_previous_move = 0
                    ticks_since_previous_action = 0
                }
            }
            %1111111101111111 -> {
                if ticks_since_previous_action > 200 {
                    drop_down_immediately()
                    ticks_since_previous_action = 0
                }
            }
            %1111111110111111 -> {
                if ticks_since_previous_action > 20 {
                    rotate_counterclockwise()
                    ticks_since_previous_action = 0
                }
            }
            %1011111111111111, %0111111111111111 -> {
                if ticks_since_previous_action > 20 {
                    rotate_clockwise()
                    ticks_since_previous_action = 0
                }
            }
            %1111111111110111 -> {
                if ticks_since_previous_action > 60 {
                    hold_block()
                    ticks_since_previous_action = 0
                }
            }
            $ffff -> {
                ; no button pressed, reset timers to allow button tapping
                ticks_since_previous_move = 255
                ticks_since_previous_action = 255
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
        if num_lines!=0 {
            if num_lines>3 {
                sound.lineclear_big()
                sys.wait(25) ; slight delay to flash the line
            }
            else {
                sound.lineclear()
                sys.wait(15) ; slight delay to flash the line
            }
            for linepos in complete_lines
                if linepos!=0 and blocklogic.isLineFull(linepos)
                    blocklogic.collapse(linepos)
            lines += num_lines
            uword[] scores = [10, 25, 50, 100]      ; can never clear more than 4 lines at once
            score += scores[num_lines-1]
            speedlevel = 1+lsb(lines/10)
            drawScore()
        }
    }

    sub gameOver() {
        const ubyte yoffset = 7
        sound.gameover()
        txt.plot(7, yoffset)
        txt.chrout('U')
        txt.print("────────────────────────")
        txt.chrout('I')
        txt.plot(7, yoffset+1)
        txt.print("│*** g a m e  o v e r ***│")
        txt.plot(7, yoffset+2)
        txt.chrout('J')
        txt.print("────────────────────────")
        txt.chrout('K')

        txt.plot(7, yoffset+11)
        txt.chrout('U')
        txt.print("────────────────────────")
        txt.chrout('I')
        txt.plot(7, yoffset+12)
        txt.print("│ f1/start for new game  │")
        txt.plot(7, yoffset+13)
        txt.chrout('J')
        txt.print("────────────────────────")
        txt.chrout('K')

        ubyte key
        do {
            ; endless loop until user presses F1 or Start button to restart the game
            cx16.r0, void = cx16.joystick_get(1)
            if cx16.r0 & %0000000000010000 == 0
                break
            void, key = cbm.GETIN()
        } until key==133
    }

    sub newGame() {
        lines = 0
        score = 0
        xpos = startXpos
        ypos = startYpos
        speedlevel = 1
        nextBlock = math.rnd() % 7
        holding = 255
        holdingAllowed = true
        ticks_since_previous_action = 0
        ticks_since_previous_move = 0
    }

    sub swapBlock(ubyte newblock) {
        blocklogic.newCurrentBlock(newblock)
        xpos = startXpos
        ypos = startYpos
        drawBlock(xpos, ypos, false)
    }

    sub spawnNextBlock() {
        swapBlock(nextBlock)
        nextBlock = (math.rnd() + lsb(cbm.RDTIM16())) % 7
        drawNextBlock()
        holdingAllowed = true
    }

    sub drawBoard() {
        const ubyte yoffset = 2
        txt.clear_screen()
        txt.color(7)
        txt.plot(1,1+yoffset)
        txt.print("* tehtriz *")
        txt.color(5)
        txt.plot(6,4+yoffset)
        txt.print("hold:")
        txt.plot(2,22+yoffset)
        txt.print("speed: ")
        txt.plot(28,3+yoffset)
        txt.print("next:")
        txt.plot(28,10+yoffset)
        txt.print("lines:")
        txt.plot(28,14+yoffset)
        txt.print("score:")
        txt.color(12)
        txt.plot(27,18+yoffset)
        txt.print("controls:")
        txt.color(11)
        txt.plot(28,19+yoffset)
        txt.print(",/  move")
        txt.plot(28,20+yoffset)
        txt.print("zx  rotate")
        txt.plot(29,21+yoffset)
        txt.print(".  descend")
        txt.plot(27,22+yoffset)
        txt.print("spc  drop")
        txt.plot(29,23+yoffset)
        txt.print("c  hold")
        txt.plot(25,24+yoffset)
        txt.print("or joystick #1")

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
                txt.setcc(6+x-i, 11+2*i+yoffset, 102, colors[i])
            }
        }
        drawScore()
    }

    sub drawScore() {
        const ubyte yoffset=2
        txt.color(1)
        txt.plot(30,11+yoffset)
        txt.print_uw(lines)
        txt.plot(30,15+yoffset)
        txt.print_uw(score)
        txt.plot(9,22+yoffset)
        txt.print_ub(speedlevel)
    }

    sub drawNextBlock() {
        const ubyte nextBlockXpos = 29
        const ubyte nextBlockYpos = 7
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
        const ubyte holdBlockYpos = 8
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

    sub drawBlock(ubyte x, ubyte y, bool erase) {
        ubyte char = 79   ; top left edge
        if erase
            char = 32   ; space
        ubyte @zp i
        for i in 15 downto 0 {
            ubyte @zp c=blocklogic.currentBlock[i]
            if c!=0 {
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

    sub canRotateCW(ubyte xpos, ubyte ypos) -> bool {
        rotateCW()
        bool nocollision = noCollision(xpos, ypos)
        rotateCCW()
        return nocollision
    }

    sub canRotateCCW(ubyte xpos, ubyte ypos) -> bool {
        rotateCCW()
        bool nocollision = noCollision(xpos, ypos)
        rotateCW()
        return nocollision
    }

    sub noCollision(ubyte xpos, ubyte ypos) -> bool {
        ubyte @zp i
        for i in 15 downto 0 {
            if currentBlock[i]!=0 and txt.getchr(xpos + (i&3), ypos+i/4)!=32
                return false
        }
        return true
    }

    sub isGameOver(ubyte xpos, ubyte ypos) -> bool {
        main.drawBlock(xpos, ypos, true)
        bool result = ypos==main.startYpos and not noCollision(xpos, ypos+1)
        main.drawBlock(xpos, ypos, false)
        return result
    }

    sub isLineFull(ubyte ypos) -> bool {
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
        psg2.init()
        cx16.enable_irq_handlers(true)
        cx16.set_vsync_irq_handler(&psg2.update)
    }

    sub blockrotate() {
        ; soft click/"tschk" sound
        psg2.voice(0, psg2.LEFT | psg2.RIGHT, 32, psg2.NOISE, 0)
        psg2.frequency(0, 15600)
        psg2.envelope(0, 200, 0, 255)
    }

    sub blockdrop() {
        ; swish
        psg2.voice(1, psg2.LEFT | psg2.RIGHT, 40, psg2.NOISE, 0)
        psg2.frequency(1, 4600)
        psg2.envelope(1, 200, 5, 30)
    }

    sub swapping() {
        ; beep
        psg2.voice(2, psg2.LEFT | psg2.RIGHT, 40, psg2.TRIANGLE, 0)
        psg2.frequency(2, 1500)
        psg2.envelope(2, 200, 2, 40)
    }

    sub lineclear() {
        ; explosion
        psg2.voice(3, psg2.LEFT | psg2.RIGHT, 63, psg2.NOISE, 0)
        psg2.frequency(3, 1400)
        psg2.envelope(3, 100, 8, 10)
    }

    sub lineclear_big() {
        ; big explosion
        psg2.voice(4, psg2.LEFT | psg2.RIGHT, 63, psg2.NOISE, 0)
        psg2.frequency(4, 2500)
        psg2.envelope(4, 100, 20, 10)
    }

    sub gameover() {
        ; buzz/boing
        psg2.voice(5, psg2.LEFT | psg2.RIGHT, 50, psg2.SAWTOOTH, 0)
        psg2.voice(6, psg2.LEFT | psg2.RIGHT, 53, psg2.TRIANGLE, 0)
        psg2.frequency(5, 200)
        psg2.frequency(6, 600)
        psg2.envelope(5, 200, 5, 20)
        psg2.envelope(6, 255, 5, 20)
    }
}
