%import textio
%import mcf

%zeropage basicsafe

main {

    sub start() {
        uword duration
        ubyte[256] bonkbuffer

        ;; diskio.fastmode(1)
        set_screen_mode()
        cbm.SETTIM(0,0,0)

        mcf.set_callbacks(mcf_get_buffer, mcf_process_chunk)        ; not needed if the stream has no custom chunk types
        mcf.set_bonkbuffer(bonkbuffer)

        if mcf.open("demo.mcf", 8, 2) {
            repeat {
                mcf.stream()
                if_cs {
                    break   ; EOF reached, stop the streaming loop
                } else {
                    ; PAUSE chunk encountered, code is in cx16.r0
                }
            }
            mcf.close()
        }

        duration = cbm.RDTIM16()
        cbm.CINT()
        txt.print("done. ")
        txt.print_uw(duration)
        txt.print(" jiffies.\n")
    }

    sub set_screen_mode() {
        ; 640x400 16 colors
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000      ; enable only layer 1
        cx16.VERA_DC_HSCALE = 128
        cx16.VERA_DC_VSCALE = 128
        cx16.VERA_CTRL = %00000010
        cx16.VERA_DC_VSTART = 20
        cx16.VERA_DC_VSTOP = 400 /2 -1 + 20 ; clip off screen that overflows vram
        cx16.VERA_L1_CONFIG = %00000110     ; 16 colors bitmap mode
        cx16.VERA_L1_MAPBASE = 0
        cx16.VERA_L1_TILEBASE = %00000001   ; hires
    }

    asmsub mcf_get_buffer(ubyte chunktype @A, uword size @XY) -> ubyte @A, uword @XY, bool @Pc {
        %asm {{
            ldx  #<$a000
            ldy  #>$a000
            lda  #10
            clc
            rts
        }}
    }

    asmsub mcf_process_chunk() -> bool @Pc {
        ; process the chunk that was loaded in the location returned by the previous call to mcf_get_buffer()
        %asm {{
            clc
            rts
        }}
    }
}
