%import textio
%import diskio
%import floats
%import graphics
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {

;    uword adres
;    ubyte adreshi


    sub start () {
        txt.print("hello\n")

        gfx2.set_mode(128)
        gfx2.clear_screen()

        uword offset
        ubyte angle
        uword x
        uword y
        when gfx2.active_mode {
            0 -> {
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

    ubyte active_mode = 255
    uword width = 0
    uword height = 0
    ubyte bpp = 0

    sub set_mode(ubyte mode) {
        ; mode 0 = bitmap 320 x 240 x 256c
        ; mode 128 = bitmap 640 x 480 x 1c monochrome
        ; ...

        when mode {
            0 -> {
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
                ; 320 x 240 x 256c
                cx16.VERA_CTRL = 0
                cx16.VERA_ADDR_H = %00010000
                cx16.VERA_ADDR_M = 0
                cx16.VERA_ADDR_L = 0
                repeat 240/4
                    cs_innerloop1280()
            }
            128 -> {
                ; 640 x 480 x 1c
                cx16.VERA_CTRL = 0
                cx16.VERA_ADDR_H = %00010000
                cx16.VERA_ADDR_M = 0
                cx16.VERA_ADDR_L = 0
                repeat 480/16
                    cs_innerloop1280()
            }
        }
    }

    asmsub cs_innerloop1280() {
        %asm {{
            ldy  #160
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


    sub plot(uword x, uword y, ubyte color) {
        uword addr
        ubyte addrhi

        when active_mode {
            0 -> {
                addr = y
                addr_mul_24_320()
                addr_add_word_24(x)
                cx16.vpoke(addrhi, addr, color)
            }
            128 -> {
                ubyte[8] bits = [128, 64, 32, 16, 8, 4, 2, 1]
                cx16.vpoke_or(0, y*(640/8) + x/8, bits[lsb(x)&7])
            }
        }


        ; TODO when subs are in front of real code, they generate in place and fuck up the code. Move them to the bottom?
        asmsub addr_mul_24_320() {
            ; addr = addr * 256 + addr * 64,  bits 16-23 into addrhi
            %asm {{
            lda  addr
            sta  P8ZP_SCRATCH_B1
            lda  addr+1
            sta  addrhi
            sta  P8ZP_SCRATCH_REG
            lda  addr
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
            sta  addr
            lda  P8ZP_SCRATCH_B1
            clc
            adc  P8ZP_SCRATCH_REG
            sta  addr+1
            bcc  +
            inc  addrhi
    +		rts
            }}
        }

        asmsub addr_add_word_24(uword w @ AY) {
            %asm {{
                clc
                adc  addr
                sta  addr
                tya
                adc  addr+1
                sta  addr+1
                bcc  +
                inc  addrhi
+               rts
            }}
        }
    }
}
