%import textio
%import graphics
%import test_stack
%option no_sysinit

; TODO full-screen graphics mode library, in development.  (as replacement for the graphics routines in ROM that are constrained to 200 vertical pixels and lores mode only)


main {

    sub start () {
        ubyte[] modes = [0, 1, 128]
        ubyte mode
        for mode in modes {
            gfx2.set_mode(mode)
            gfx2.clear_screen()
            draw()
            cx16.wait(120)
        }

        repeat {
            ;
        }
    }

    sub draw() {
        uword offset
        ubyte angle
        uword x
        uword y
        when gfx2.active_mode {
            0, 1 -> {
                for offset in 0 to 90 step 3 {
                    for angle in 0 to 255 {
                        x = $0008+sin8u(angle)/2
                        y = $0008+cos8u(angle)/2
                        gfx2.plot(x+offset*2,y+offset, lsb(x+y))
                    }
                }
            }
            128 -> {
                for offset in 0 to 190 step 6 {
                    for angle in 0 to 255 {
                        x = $0008+sin8u(angle)
                        y = $0008+cos8u(angle)
                        gfx2.plot(x+offset*2,y+offset, 1)
                    }
                }
            }
        }
    }
}

gfx2 {

    ; read-only control variables:
    ubyte active_mode = 255
    uword width = 0
    uword height = 0
    ubyte bpp = 0


    sub set_mode(ubyte mode) {
        ; mode 0 = bitmap 320 x 240 x 1c monochrome
        ; mode 1 = bitmap 320 x 240 x 256c
        ; mode 128 = bitmap 640 x 480 x 1c monochrome
        ; ...other modes?

        when mode {
            0 -> {
                ; 320 x 240 x 1c
                cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
                cx16.VERA_DC_HSCALE = 64
                cx16.VERA_DC_VSCALE = 64
                cx16.VERA_L1_CONFIG = %00000100
                cx16.VERA_L1_MAPBASE = 0
                cx16.VERA_L1_TILEBASE = 0
                width = 320
                height = 240
                bpp = 1
            }
            1 -> {
                ; 320 x 240 x 256c
                cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
                cx16.VERA_DC_HSCALE = 64
                cx16.VERA_DC_VSCALE = 64
                cx16.VERA_L1_CONFIG = %00000111
                cx16.VERA_L1_MAPBASE = 0
                cx16.VERA_L1_TILEBASE = 0
                width = 320
                height = 240
                bpp = 8
            }
            128 -> {
                ; 640 x 480 x 1c
                cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
                cx16.VERA_DC_HSCALE = 128
                cx16.VERA_DC_VSCALE = 128
                cx16.VERA_L1_CONFIG = %00000100
                cx16.VERA_L1_MAPBASE = 0
                cx16.VERA_L1_TILEBASE = %00000001
                width = 640
                height = 480
                bpp = 1
            }
        }
        active_mode = mode
    }

    sub clear_screen() {
        when active_mode {
            0 -> {
                ; 320 x 240 x 1c
                cx16.VERA_CTRL = 0
                cx16.VERA_ADDR_H = %00010000
                cx16.VERA_ADDR_M = 0
                cx16.VERA_ADDR_L = 0
                repeat 240/2/8
                    cs_innerloop640()
            }
            1 -> {
                ; 320 x 240 x 256c
                cx16.VERA_CTRL = 0
                cx16.VERA_ADDR_H = %00010000
                cx16.VERA_ADDR_M = 0
                cx16.VERA_ADDR_L = 0
                repeat 240/2
                    cs_innerloop640()
            }
            128 -> {
                ; 640 x 480 x 1c
                cx16.VERA_CTRL = 0
                cx16.VERA_ADDR_H = %00010000
                cx16.VERA_ADDR_M = 0
                cx16.VERA_ADDR_L = 0
                repeat 480/8
                    cs_innerloop640()
            }
        }
    }

    sub plot(uword x, uword y, ubyte color) {
        ubyte[8] bits = [128, 64, 32, 16, 8, 4, 2, 1]
        when active_mode {
            0 -> {
                cx16.vpoke_or(0, y*(320/8) + x/8, bits[lsb(x)&7])
            }
            1 -> {
                void addr_mul_320_add_24(y, x)      ; 24 bits result is in r0 and r1L
                cx16.vpoke(lsb(cx16.r1), cx16.r0, color)
            }
            128 -> {
                cx16.vpoke_or(0, y*(640/8) + x/8, bits[lsb(x)&7])
            }
        }
        return


        asmsub addr_mul_320_add_24(uword address @R0, uword value @AY) -> uword @R0, ubyte @R1  {
            %asm {{
                sta  P8ZP_SCRATCH_W1
                sty  P8ZP_SCRATCH_W1+1
                lda  cx16.r0
                sta  P8ZP_SCRATCH_B1
                lda  cx16.r0+1
                sta  cx16.r1
                sta  P8ZP_SCRATCH_REG
                lda  cx16.r0
                asl  a
                rol  P8ZP_SCRATCH_REG
                asl  a
                rol  P8ZP_SCRATCH_REG
                asl  a
                rol  P8ZP_SCRATCH_REG
                asl  a
                rol  P8ZP_SCRATCH_REG
                asl  a
                rol  P8ZP_SCRATCH_REG
                asl  a
                rol  P8ZP_SCRATCH_REG
                sta  cx16.r0
                lda  P8ZP_SCRATCH_B1
                clc
                adc  P8ZP_SCRATCH_REG
                sta  cx16.r0+1
                bcc  +
                inc  cx16.r1
+		        ; now add the value to this 24-bits number
                lda  cx16.r0
                clc
                adc  P8ZP_SCRATCH_W1
                sta  cx16.r0
                lda  cx16.r0+1
                adc  P8ZP_SCRATCH_W1+1
                sta  cx16.r0+1
                bcc  +
                inc  cx16.r1
+               lda  cx16.r1
                rts
            }}
        }
    }

    asmsub cs_innerloop640() {
        %asm {{
            ldy  #80
-           stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            stz  cx16.VERA_DATA0
            dey
            bne  -
            rts
        }}
    }
}
