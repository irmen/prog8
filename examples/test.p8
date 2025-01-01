%import textio
%import math
%import monogfx
%import gfx_lores
%option no_sysinit

main {
    sub start() {

        math.rndseed(1234,8877)
        cbm.SETTIM(0,0,0)
        kernal()
        txt.print_uw(cbm.RDTIM16())

        sys.wait(200)

        math.rndseed(1234,8877)
        cbm.SETTIM(0,0,0)
        custom_256()
        txt.print_uw(cbm.RDTIM16())

        sys.wait(200)


        math.rndseed(1234,8877)
        cbm.SETTIM(0,0,0)
        custom_mono()
        txt.print_uw(cbm.RDTIM16())

        repeat {
        }
    }

    sub kernal() {
        cx16.set_screen_mode(128)
        cx16.GRAPH_set_colors(2,1,0)
        cx16.GRAPH_clear()
        repeat 1000 {
            cx16.r0 = math.rndw() % 320
            cx16.r1 = math.rnd() % 240
            cx16.r2 = math.rndw() % 320
            cx16.r3 = math.rnd() % 240
            cx16.GRAPH_draw_line(cx16.r0, cx16.r1, cx16.r2, cx16.r3)
        }
        cx16.set_screen_mode(0)
    }

    sub custom_mono() {
        monogfx.lores()
        repeat 1000 {
            cx16.r0 = math.rndw() % 320
            cx16.r1L = math.rnd() % 240
            cx16.r2 = math.rndw() % 320
            cx16.r3L = math.rnd() % 240
            line(cx16.r0, cx16.r1L, cx16.r2, cx16.r3L)
        }
        monogfx.textmode()
    }

    sub custom_256() {
        gfx_lores.graphics_mode()
        repeat 1000 {
            cx16.r0 = math.rndw() % 320
            cx16.r1L = math.rnd() % 240
            cx16.r2 = math.rndw() % 320
            cx16.r3L = math.rnd() % 240
            gfx_lores.line(cx16.r0, cx16.r1L, cx16.r2, cx16.r3L, 2)
        }
        gfx_lores.text_mode()
    }

    sub line(uword @zp x1, ubyte @zp y1, uword @zp x2, ubyte @zp y2) {
        ; Bresenham algorithm.
        ; This code special-cases various quadrant loops to allow simple ++ and -- operations.
        if y1>y2 {
            ; make sure dy is always positive to have only 4 instead of 8 special cases
            cx16.r0 = x1
            x1 = x2
            x2 = cx16.r0
            cx16.r0L = y1
            y1 = y2
            y2 = cx16.r0L
        }
        word @zp dx = (x2 as word)-x1
        ubyte @zp dy = y2-y1

        if dx==0 {
            monogfx.vertical_line(x1, y1, abs(dy) as uword +1, true)
            return
        }
        if dy==0 {
            if x1>x2
                x1=x2
            monogfx.horizontal_line(x1, y1, abs(dx) as uword +1, true)
            return
        }

        cx16.r1L = 1 ;; true      ; 'positive_ix'
        if dx < 0 {
            dx = -dx
            cx16.r1L = 0 ;; false
        }
        word @zp dx2 = dx*2
        word @zp dy2 = dy*2
        word @zp d = 0
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_H = 0
        if dx >= dy {
            if cx16.r1L!=0 {
                repeat {
                    plot()
                    if x1==x2
                        return
                    x1++
                    d += dy2
                    if d > dx {
                        y1++
                        d -= dx2
                    }
                }
            } else {
                repeat {
                    plot()
                    if x1==x2
                        return
                    x1--
                    d += dy2
                    if d > dx {
                        y1++
                        d -= dx2
                    }
                }
            }
        }
        else {
            if cx16.r1L!=0 {
                repeat {
                    plot()
                    if y1==y2
                        return
                    y1++
                    d += dx2
                    if d > dy {
                        x1++
                        d -= dy2
                    }
                }
            } else {
                repeat {
                    plot()
                    if y1==y2
                        return
                    y1++
                    d += dx2
                    if d > dy {
                        x1--
                        d -= dy2
                    }
                }
            }
        }

        asmsub plot() {
            %asm {{
                lda  p8v_x1+1
                lsr  a
                lda  p8v_x1
                ror  a
                lsr  a
                lsr  a

                clc
                ldy  p8v_y1
                adc  times40_lo,y
                sta  cx16.VERA_ADDR_L
                lda  times40_mid,y
                adc  #0
                sta  cx16.VERA_ADDR_M

                lda  p8v_x1
                and  #7
                tax
                lda  maskbits,x
                tsb  cx16.VERA_DATA0
                rts

maskbits    .byte  128,64,32,16,8,4,2,1
; multiplication by 40 lookup table
times40 := 40*range(240)

times40_lo     .byte <times40
times40_mid    .byte >times40

            ; !notreached!
    }}
        }

    }


}
