%import diskio
%import palette
%import sprites
%option no_sysinit

; play a raw pcm stereo 16 bit audio file at 16021 hz,
; with real-time VU meters and sample waveform displays.

; Left as an excercise for the reader: add peak level indicator.
; that would require use of more sprites though, because it has to appear on top of the blocking black sprites.

main {

    const ubyte vera_rate = 42              ;  16021 hz
    str pcmfile = "thriller-16k.pcm"        ;  should be raw pcm, 16 bits signed integer, stereo, 16021 hz.

    sub start() {
        setup()
        play_stuff()
    }

    sub setup() {
        void cx16.screen_mode(128, false)
        cx16.GRAPH_set_colors(0,0,0)
        cx16.GRAPH_clear()

        cx16.GRAPH_set_colors(47,0,0)
        cx16.r0 = 250
        cx16.r1 = 10
        for cx16.r9L in iso:"made in Prog8"
            cx16.GRAPH_put_next_char(cx16.r9L)
        cx16.r0 = 250
        cx16.r1 = 20
        for cx16.r9L in iso:"16kHz stereo"
            cx16.GRAPH_put_next_char(cx16.r9L)

        cx16.rombank(0)                     ; activate kernal bank for faster calls
        void diskio.fastmode(1)

        cx16.VERA_AUDIO_RATE = 0            ; halt playback
        cx16.VERA_AUDIO_CTRL = %10111100    ; stereo 16 bit, volume 12
        repeat 1024
            cx16.VERA_AUDIO_DATA = 0        ; fill buffer with short silence

        ; vu bar sprites (left and right bars, each 4 sprites)
        palette.set_color(15, $000)   ; make sprites black
        for cx16.r9L in 0 to 7 {
            sprites.init(cx16.r9L, 1, $3000, sprites.SIZE_32, sprites.SIZE_64, sprites.COLORS_16, 0)
        }
        for cx16.r9 in $3000 to $3000+32*64/2 {
            cx16.vpoke(1, cx16.r9, $ff)
        }

        ; move the vu sprites to the base positions
        update_vu()

        ; draw vu gradient bars.
        ; 18 bars from the bottom are gradient from green to red
        ; 4 bars on top of that are just red for the extremes
        ubyte[18] gradient_colors_outline = [ 2,  2,  2,  2, 51, 51,  8,  8,  5,  5,  5,  5, 141, 141, 141, 141, 140, 140]
        ubyte[18] gradient_colors_fill =    [59, 59, 52, 52,  8,  8, 80, 80, 13, 13, 13, 13, 143, 143, 143, 143, 142, 142]

        for cx16.r9L in 0 to len(gradient_colors_fill)-1 {
            cx16.GRAPH_set_colors(gradient_colors_outline[cx16.r9L], gradient_colors_fill[cx16.r9L], 0)
            cx16.GRAPH_draw_rect(160-32-16, cx16.r9L * 8 + $0060, 32, 7, 0, true)
            cx16.GRAPH_draw_rect(160+32-16, cx16.r9L * 8 + $0060, 32, 7, 0, true)
        }
        for cx16.r9L in 0 to 3 {
            cx16.GRAPH_set_colors(gradient_colors_outline[0], gradient_colors_fill[0], 0)
            cx16.GRAPH_draw_rect(160-32-16, cx16.r9L * 8 + $0040, 32, 7, 0, true)
            cx16.GRAPH_draw_rect(160+32-16, cx16.r9L * 8 + $0040, 32, 7, 0, true)
        }

        ; waveform sprites 32x64
        sprites.init(16, 1, $3400, sprites.SIZE_32, sprites.SIZE_64, sprites.COLORS_16, 0)
        sprites.init(17, 1, $3800, sprites.SIZE_32, sprites.SIZE_64, sprites.COLORS_16, 0)
        for cx16.r9 in $3400 to $3400+32*64/2 {
            cx16.vpoke(1, cx16.r9, 0)
            cx16.vpoke(1, cx16.r9+$0400, 0)
        }
        sprites.pos(16, 160-100-16, 120)
        sprites.pos(17, 160+100-16, 120)

        ; activate irq handlers
        cx16.enable_irq_handlers(true)
        cx16.set_aflow_irq_handler(interrupts.aflow_handler)
        cx16.set_vsync_irq_handler(interrupts.vsync_handler)
    }

    sub play_stuff() {
        if diskio.f_open(pcmfile) {
            music.pre_buffer()
            cx16.VERA_AUDIO_RATE = vera_rate    ; start audio playback

            repeat {
                interrupts.wait()
                if interrupts.vsync {
                    interrupts.vsync=false
                    update_visuals()
                }
                if interrupts.aflow {
                    interrupts.aflow=false
                    if not music.load_next_block()
                        break
                    ; Note: copying the samples into the fifo buffer is done by the aflow interrupt handler itself.
                    collect_audio_volumes()
                }
            }

            diskio.f_close()
        } else {
            txt.print("load error\n")
        }

        cx16.VERA_AUDIO_RATE = 0                ; halt playback
    }

    uword @zp current_vol_left
    uword @zp current_vol_right
    uword avg_vol_left
    uword avg_vol_right
    ubyte vu_refresh_ticks = 2

    sub collect_audio_volumes() {
        current_vol_left=0
        current_vol_right=0
        uword @zp buf_ptr = music.buffer + 1

        sys.set_irqd()
        repeat music.PCM_BLOCK_SIZE/2/2 {
            current_vol_left += scale[abs(@(buf_ptr) as byte)]
            buf_ptr+=2
            current_vol_right += scale[abs(@(buf_ptr) as byte)]
            buf_ptr+=2
        }
        sys.clear_irqd()

        ; logarithmic volume scale  ln(x/20+1)*97
        ubyte[256] scale = [
            0, 4, 9, 13, 17, 21, 25, 29, 32, 36, 39, 42, 45, 48, 51, 54, 57, 59, 62, 64, 67, 69, 71, 74, 76,
            78, 80, 82, 84, 86, 88, 90, 92, 94, 96, 98, 99, 101, 103, 104, 106, 108, 109, 111, 112, 114, 115,
            117, 118, 120, 121, 122, 124, 125, 126, 128, 129, 130, 132, 133, 134, 135, 136, 138, 139, 140, 141,
            142, 143, 144, 145, 146, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 158, 159, 160, 161,
            162, 163, 164, 165, 166, 167, 167, 168, 169, 170, 171, 172, 172, 173, 174, 175, 176, 176, 177, 178,
            179, 180, 180, 181, 182, 183, 183, 184, 185, 185, 186, 187, 188, 188, 189, 190, 190, 191, 192, 192,
            193, 194, 194, 195, 196, 196, 197, 197, 198, 199, 199, 200, 201, 201, 202, 202, 203, 204, 204, 205,
            205, 206, 207, 207, 208, 208, 209, 209, 210, 210, 211, 212, 212, 213, 213, 214, 214, 215, 215, 216,
            216, 217, 217, 218, 218, 219, 219, 220, 220, 221, 221, 222, 222, 223, 223, 224, 224, 225, 225, 226,
            226, 227, 227, 228, 228, 229, 229, 229, 230, 230, 231, 231, 232, 232, 233, 233, 233, 234, 234, 235,
            235, 236, 236, 236, 237, 237, 238, 238, 238, 239, 239, 240, 240, 241, 241, 241, 242, 242, 243, 243,
            243, 244, 244, 244, 245, 245, 246, 246, 246, 247, 247, 248, 248, 248, 249, 249, 249, 250, 250, 251,
            251, 251, 252, 252, 252, 253, 253, 253, 254]
    }

    sub update_visuals() {
        vu_refresh_ticks--
        if_z {
            vu_refresh_ticks = 2
            update_vu()
            avg_vol_left = 0
            avg_vol_right = 0
        } else {
            avg_vol_left += current_vol_left
            avg_vol_right += current_vol_right
        }

        uword sample_ptr = music.buffer
        cx16.vaddr(1,$3400,0,1)
        repeat 64 {
            sample_line()
            sample_ptr+=8       ; should be 16 to cover whole buffer, but this looks nicer
        }
        cx16.vaddr(1,$3800,0,1)
        sample_ptr = music.buffer+2
        repeat 64 {
            sample_line()
            sample_ptr+=8       ; should be 16 to cover whole buffer, but this looks nicer
        }

        sub sample_line() {
            cx16.r0L = @(sample_ptr+1) >> 3        ; value 0 - 31
            %asm {{
                ldy  cx16.r0L
                lda  p8v_sample_sprite_tab_0,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_1,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_2,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_3,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_4,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_5,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_6,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_7,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_8,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_9,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_a,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_b,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_c,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_d,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_e,y
                sta  cx16.VERA_DATA0
                lda  p8v_sample_sprite_tab_f,y
                sta  cx16.VERA_DATA0
            }}
;            cx16.VERA_DATA0 = sample_sprite_tab_0[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_1[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_2[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_3[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_4[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_5[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_6[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_7[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_8[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_9[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_a[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_b[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_c[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_d[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_e[cx16.r0L]
;            cx16.VERA_DATA0 = sample_sprite_tab_f[cx16.r0L]

            ubyte[32] sample_sprite_tab_0 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $10,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_1 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$10,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_2 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$10,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_3 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$01,$01,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_4 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$10,$01,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_5 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$10,$01,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_6 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$10,$01,$00,$00]
            ubyte[32] sample_sprite_tab_7 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$10,$01]
            ubyte[32] sample_sprite_tab_8 = [$10,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_9 = [$00,$00,$10,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_a = [$00,$00,$00,$00,$10,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_b = [$00,$00,$00,$00,$00,$00,$10,$01,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_c = [$00,$00,$00,$00,$00,$00,$00,$00,$10,$01,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_d = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$10,$01,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_e = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$10,$01,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_f = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$10,$10, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
        }
    }

    sub update_vu() {
        ; determine vu 'level' in steps of 8 pixels
        word aleft = ($00a0 as word - msb(avg_vol_left)) & %11111111_11111000
        word aright = ($00a0 as word - msb(avg_vol_right)) & %11111111_11111000
        for cx16.r9L in 0 to 3 {
            ; note: sprites overlap a bit to avoid scanlines peeping through when updating outside of vblank
            sprites.pos(cx16.r9L, 160-32-16, aleft)
            sprites.pos(4+cx16.r9L, 160+32-16, aright)
            aleft -= 32
            aright -= 32
        }
    }
}


interrupts {

    bool aflow
    bool vsync

    asmsub wait() {
        %asm {{
            wai
        }}
    }

    sub aflow_handler() -> bool {
        ; Filling the fifo is the only way to clear the Aflow irq.
        ; So we do this here, otherwise the aflow irq will keep triggering.
        ; Note that filling the buffer with fresh audio samples is NOT done here,
        ; but instead in the main program code that triggers on the 'aflow' being true!
        cx16.save_virtual_registers()
        music.fill_fifo()
        cx16.restore_virtual_registers()
        aflow = true
        return false
    }

    sub vsync_handler() -> bool {
        vsync = true
        return false
    }
}


music {
    const uword PCM_BLOCK_SIZE = 1024        ; must be power of 2
    uword buffer = memory("buffer", PCM_BLOCK_SIZE, 256)

    sub pre_buffer() {
        ; pre-buffer first block
        void diskio.f_read(buffer, PCM_BLOCK_SIZE)
    }

    sub load_next_block() -> bool {
        ; read next block from disk into the buffer, for next time the irq triggers
        return diskio.f_read(buffer, PCM_BLOCK_SIZE) == PCM_BLOCK_SIZE
    }

    asmsub fill_fifo() {
        ; optimized loop to put <block_size> bytes of data into the fifo as fast as possible
        %asm {{
            lda  p8v_buffer
            sta  cx16.r0L
            lda  p8v_buffer+1
            sta  cx16.r0H
            ldx  #(p8c_PCM_BLOCK_SIZE>>8)
-           ldy  #0
-           lda  (cx16.r0),y
            sta  cx16.VERA_AUDIO_DATA
            iny
            bne  -
            inc  cx16.r0H
            dex
            bne  --
            rts
        }}
; original prog8 code:
;        uword @requirezp ptr = main.start.buffer
;        repeat 1024 {
;            cx16.VERA_AUDIO_DATA = @(ptr)
;            ptr++
;        }
    }
}
