%import math
%import gfx2

main {
    sub start() {
        gfx2.screen_mode(4)

        uword[128] xx
        uword[128] yy

        ubyte @zp idx
        for idx in 0 to 127 {
            xx[idx] = math.rndw() % 320
            yy[idx] = math.rndw() % 240
        }

        gfx2.text(96, 90, 2, sc:"**************")
        gfx2.text(104, 100, 5, sc:"let it snow!")
        gfx2.text(96, 110, 2, sc:"**************")

        const ubyte FALLING_SNOW_COLOR = 1
        const ubyte PILED_SNOW_COLOR = 15

        repeat {
            sys.waitvsync()
            for idx in 0 to 127 {
                gfx2.plot(xx[idx], yy[idx], FALLING_SNOW_COLOR)
            }
            sys.waitvsync()
            for idx in 0 to 127 {
                if yy[idx]==239 {
                    ; reached the floor
                    gfx2.plot(xx[idx], yy[idx], PILED_SNOW_COLOR)
                    yy[idx] = 0
                    xx[idx] = math.rndw() % 320
                } else if gfx2.pget(xx[idx], yy[idx]+1)==PILED_SNOW_COLOR {
                    ; pile up
                    uword @zp snowx = xx[idx]
                    if snowx!=0 and snowx!=319 {        ; check to avoid x coordinate under/overflow
                        uword pilex1
                        uword pilex2
                        if math.rnd() & 1 {
                            pilex1 = snowx-1
                            pilex2 = snowx+1
                        } else {
                            pilex1 = snowx+1
                            pilex2 = snowx-1
                        }
                        if gfx2.pget(pilex1, yy[idx]+1)==0 {
                            gfx2.plot(snowx, yy[idx], 0)
                            gfx2.plot(pilex1, yy[idx]+1, PILED_SNOW_COLOR)
                        } else if gfx2.pget(pilex2, yy[idx]+1)==0 {
                            gfx2.plot(snowx, yy[idx], 0)
                            gfx2.plot(pilex2, yy[idx]+1, PILED_SNOW_COLOR)
                        } else {
                            gfx2.plot(snowx, yy[idx], PILED_SNOW_COLOR)
                        }
                    }
                    yy[idx] = 0
                    xx[idx] = math.rndw() % 320
                } else {
                    ; fall
                    gfx2.plot(xx[idx], yy[idx], 0)
                    yy[idx]++
                    when math.rnd() & 3 {
                        1 -> {
                            if xx[idx]
                                xx[idx]--
                        }
                        2 -> {
                            if xx[idx] < 319
                                xx[idx]++
                        }
                    }
                }
            }
        }

        repeat {
        }
    }
}
