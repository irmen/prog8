%import math
%import gfx_lores

main {
    sub start() {
        gfx_lores.graphics_mode()

        uword[128] flakes1_xx
        ubyte[128] flakes1_yy
        uword[128] flakes2_xx
        ubyte[128] flakes2_yy

        ubyte @zp idx
        for idx in 0 to 127 {
            flakes1_xx[idx] = math.rndw() % 320
            flakes1_yy[idx] = math.rnd() % 240
            flakes2_xx[idx] = math.rndw() % 320
            flakes2_yy[idx] = math.rnd() % 240
        }

        draw_screen()

        const ubyte FALLING_SNOW_COLOR = 1
        const ubyte FALLING_SNOW_COLOR2 = 12
        const ubyte PILED_SNOW_COLOR = 15
        ubyte[] PILED_SNOW_COLORS = [PILED_SNOW_COLOR, 5, 2]    ; falling snow colors should NOT be in here!
        bool fall_layer_2 = false

        repeat {
            for idx in 0 to len(flakes1_xx)-1 {
                gfx_lores.plot(flakes1_xx[idx], flakes1_yy[idx], FALLING_SNOW_COLOR)
                gfx_lores.plot(flakes2_xx[idx], flakes2_yy[idx], FALLING_SNOW_COLOR2)
            }
            sys.waitvsync()
            sys.waitvsync()

            for idx in 0 to len(flakes1_xx)-1 {
                if flakes1_yy[idx]==239 {
                    ; reached the floor
                    gfx_lores.plot(flakes1_xx[idx], flakes1_yy[idx], PILED_SNOW_COLOR)
                    flakes1_yy[idx] = 0
                    flakes1_xx[idx] = math.rndw() % 320
                } else if gfx_lores.pget(flakes1_xx[idx], flakes1_yy[idx]+1) in PILED_SNOW_COLORS {
                    ; pile up
                    ; TODO somehow prevent growing horizontally/diagonally like a crystal
                    uword @zp snowx = flakes1_xx[idx]
                    if snowx!=0 and snowx!=319 {        ; check to avoid x coordinate under/overflow
                        uword pilex1
                        uword pilex2
                        if math.rnd() & 1 !=0 {
                            pilex1 = snowx-1
                            pilex2 = snowx+1
                        } else {
                            pilex1 = snowx+1
                            pilex2 = snowx-1
                        }
                        ubyte pixel_side1 = gfx_lores.pget(pilex1, flakes1_yy[idx]+1)
                        ubyte pixel_side2 = gfx_lores.pget(pilex2, flakes1_yy[idx]+1)
                        if pixel_side1 == 0 or pixel_side1 == FALLING_SNOW_COLOR2 {
                            gfx_lores.plot(snowx, flakes1_yy[idx], 0)
                            gfx_lores.plot(pilex1, flakes1_yy[idx]+1, PILED_SNOW_COLOR)
                        } else if pixel_side2 == 0 or pixel_side2 == FALLING_SNOW_COLOR2 {
                            gfx_lores.plot(snowx, flakes1_yy[idx], 0)
                            gfx_lores.plot(pilex2, flakes1_yy[idx]+1, PILED_SNOW_COLOR)
                        } else {
                            gfx_lores.plot(snowx, flakes1_yy[idx], PILED_SNOW_COLOR)
                        }
                    }
                    flakes1_yy[idx] = 0
                    flakes1_xx[idx] = math.rndw() % 320
                } else {
                    ; fall
                    gfx_lores.plot(flakes1_xx[idx], flakes1_yy[idx], 0)
                    flakes1_yy[idx]++
                    when math.rnd() & 3 {
                        1 -> {
                            if flakes1_xx[idx]!=0
                                flakes1_xx[idx]--
                        }
                        2 -> {
                            if flakes1_xx[idx] < 319
                                flakes1_xx[idx]++
                        }
                    }
                }

            }

            fall_layer_2 = not fall_layer_2
            if fall_layer_2 {
                for idx in 0 to len(flakes2_xx)-1 {
                    ; the second 'layer' of snowflakes
                    if flakes2_yy[idx]==239 {
                        ; reached the floor, don't pile up just fall again
                        flakes2_yy[idx] = 0
                        flakes2_xx[idx] = math.rndw() % 320
                    } else if gfx_lores.pget(flakes2_xx[idx], flakes2_yy[idx]+1) in PILED_SNOW_COLORS {
                        ; reached an obstruction, don't pile up just fall again
                        flakes2_yy[idx] = 0
                        flakes2_xx[idx] = math.rndw() % 320
                    } else {
                        ; fall normally
                        gfx_lores.plot(flakes2_xx[idx], flakes2_yy[idx], 0)
                        flakes2_yy[idx]+=1
                        when math.rnd() & 3 {
                            1 -> {
                                if flakes2_xx[idx]!=0
                                    flakes2_xx[idx]--
                            }
                            2 -> {
                                if flakes2_xx[idx] < 319
                                    flakes2_xx[idx]++
                            }
                        }
                    }
                }
            }
        }

        repeat {
        }
    }

    sub draw_screen() {
        gfx_lores.text(32, 130, 2, sc: "******************" )
        gfx_lores.text(40, 140, 5, sc:  "happy holidays !"  )
        gfx_lores.text(32, 150, 5, sc: "from commander x16" )
        gfx_lores.text(32, 160, 2, sc: "******************" )

        ; draw a tree

        const uword TREEX = 240
        ubyte maxwidth = 20
        ubyte branchesy = 100

        gfx_lores.fillrect(TREEX-5, 210, 10, 30, 9)
        repeat 5 {
            ubyte width
            for width in 1 to maxwidth {
                gfx_lores.horizontal_line(TREEX-width/2, branchesy, width, 5)
                branchesy++
            }
            branchesy -= maxwidth/2
            maxwidth += 8
        }
    }
}
