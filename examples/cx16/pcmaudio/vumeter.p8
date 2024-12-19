%import diskio
%import sprites
%option no_sysinit

; play a raw pcm stereo 16 bit audio file at 16021 hz,
; with real-time VU meters and sample waveform displays.

; you can make an appropriate pcm file with one of the following commands:
;   ffmpeg -i input_audio_file -ac 2 -ar 16021 -f s16le -acodec pcm_s16le music.pcm
;   sox input_audio_file -e signed-integer -L -b 16 -c 2 -r 16021 -t raw music.pcm


main {

    const ubyte vera_rate = 42       ;  16021 hz
    str pcmfile = "music.pcm"        ;  see format specs mentioned above

    sub start() {
        setup()
        play_stuff()
        repeat {}
    }

    sub setup() {
        cx16.set_screen_mode(128)
        cx16.GRAPH_init(0)
        cx16.GRAPH_set_colors(0,0,0)
        cx16.GRAPH_clear()

        cx16.GRAPH_set_colors(6,0,0)
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

        ; clear vu meter to base values
        update_vu()

        ; draw the vu gradient bars (20 leds each)
        ; They use palette colors 16 and up, in pairs. Left bar first (16-55) then right bar (56-95).
        for cx16.r9L in 0 to 19 {
            cx16.GRAPH_set_colors(16+cx16.r9L*2, 17+cx16.r9L*2, 0)
            cx16.GRAPH_draw_rect(160-32-16, 220-8-cx16.r9L*8, 32, 7, 0, true)
            cx16.GRAPH_set_colors(56+cx16.r9L*2, 57+cx16.r9L*2, 0)
            cx16.GRAPH_draw_rect(160+32-16, 220-8-cx16.r9L*8, 32, 7, 0, true)
        }

        ; waveform sprites 32x64
        sprites.init(16, 1, $3400, sprites.SIZE_32, sprites.SIZE_64, sprites.COLORS_16, 0)
        sprites.init(17, 1, $3800, sprites.SIZE_32, sprites.SIZE_64, sprites.COLORS_16, 0)
        for cx16.r9 in $3400 to $3400+32*64/2 {
            cx16.vpoke(1, cx16.r9, 0)
            cx16.vpoke(1, cx16.r9+$0400, 0)
        }
        sprites.pos(16, 160-100-16, 100)
        sprites.pos(17, 160+100-16, 100)

        ; activate irq handlers
        cx16.enable_irq_handlers(true)
        cx16.set_aflow_irq_handler(interrupts.aflow_handler)
        cx16.set_vsync_irq_handler(interrupts.vsync_handler)
    }

    sub play_stuff() {
        if diskio.f_open(pcmfile) {
            bool streaming = true
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
                    if streaming {
                        streaming = music.load_next_block()
                        if not streaming {
                            cx16.VERA_AUDIO_RATE = 0            ; halt playback
                            diskio.f_close()
                            sys.memset(music.buffer, music.PCM_BLOCK_SIZE, 0)
                        }
                    }
                    ; Note: copying the samples into the fifo buffer is done by the aflow interrupt handler itself.
                    collect_audio_volumes()
                }
            }
        } else {
            txt.print("load error\n")
        }

        cx16.VERA_AUDIO_RATE = 0                ; halt playback
    }

    uword @zp current_vol_left, current_vol_right
    uword avg_vol_left, avg_vol_right
    ubyte peak_left, peak_right
    ubyte vu_refresh_ticks = 2
    ubyte peak_falloff_ticks_left = 2
    ubyte peak_falloff_ticks_right = 2
    ubyte peak_stick_delay_left
    ubyte peak_stick_delay_right

    sub collect_audio_volumes() {
        current_vol_left = current_vol_right = 0
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
            avg_vol_left = avg_vol_right = 0
        } else {
            avg_vol_left += current_vol_left
            avg_vol_right += current_vol_right
        }

        uword @zp sample_ptr = music.buffer
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

            ubyte[32] sample_sprite_tab_0 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $1c,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_1 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$c0,$1c,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_2 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$c0,$1c,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_3 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$c0,$1c,$01,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_4 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$c0,$1c,$01,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_5 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$c0,$1c,$01,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_6 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$c0,$1c,$01,$00,$00]
            ubyte[32] sample_sprite_tab_7 = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$c0,$1c,$01]
            ubyte[32] sample_sprite_tab_8 = [$1c,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_9 = [$00,$c0,$1c,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_a = [$00,$00,$00,$c0,$1c,$01,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_b = [$00,$00,$00,$00,$00,$c0,$1c,$01,$00,$00,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_c = [$00,$00,$00,$00,$00,$00,$00,$c0,$1c,$01,$00,$00,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_d = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$c0,$1c,$01,$00,$00,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_e = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$c0,$1c,$01,$00,$00, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
            ubyte[32] sample_sprite_tab_f = [$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$c0,$1c,$01, $00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00,$00]
        }
    }

    sub update_vu() {
        ; determine vu 'level' in steps of 8 pixels, on a scale of 0-19
        ubyte aleft = min(143, msb(avg_vol_left))
        ubyte aright = min(143, msb(avg_vol_right))
        aleft = (aleft >> 3) + 2
        aright = (aright >> 3) + 2

        set_inactive_leds()
        set_peak_indicator()
        set_active_leds()

        sub set_inactive_leds() {
            ubyte level
            for level in aleft*4 to 19*4 step 4 {
                set_led_colors(16*2+level, 20)
            }
            for level in aright*4 to 19*4 step 4 {
                set_led_colors(56*2+level, 20)
            }
        }

        sub set_active_leds() {
            do {
                aleft--
                set_led_colors((16+aleft*2)*2, aleft)
            } until aleft==0
            do {
                aright--
                set_led_colors((56+aright*2)*2, aright)
            } until aright==0
        }

        sub set_peak_indicator() {
            peak_stick_delay_left--
            if_z {
                peak_stick_delay_left = 1
                peak_falloff_ticks_left--
                if_z {
                    peak_falloff_ticks_left = 2
                    if peak_left!=0
                        peak_left--
                }
            }
            peak_stick_delay_right--
            if_z {
                peak_stick_delay_right = 1
                peak_falloff_ticks_right--
                if_z {
                    peak_falloff_ticks_right = 2
                    if peak_right!=0
                        peak_right--
                }
            }
            if aleft>peak_left {
                peak_stick_delay_left = 15
                peak_left = aleft
            }
            if aright>peak_right {
                peak_stick_delay_right = 15
                peak_right = aright
            }
            set_led_colors(16*2+peak_left*4, 21)
            set_led_colors(56*2+peak_right*4, 21)
        }

        sub set_led_colors(ubyte palette_offset, ubyte color_idx) {
            cx16.vaddr(1, $fa00+palette_offset, 0, 1)
            uword @zp outline_rgb = outline_color[color_idx]
            uword @zp fill_rgb = fill_color[color_idx]
            cx16.VERA_DATA0 = lsb(outline_rgb)
            cx16.VERA_DATA0 = msb(outline_rgb)
            cx16.VERA_DATA0 = lsb(fill_rgb)
            cx16.VERA_DATA0 = msb(fill_rgb)
        }

        uword[22] outline_color = [$090,$190,$290,$390,$490,$590,$690,$790,$990,$980,$970,$960,$950,$940,$a30,$a00,$a00,$a00,$a00,$a00, $111, $148]
        uword[22] fill_color    = [$0f0,$2f0,$4f0,$6f0,$8f0,$af0,$cf0,$ef0,$ff0,$fe0,$fc0,$fa0,$f80,$f60,$f40,$f00,$f01,$f02,$f03,$f04, $000, $28f]
    }
}


interrupts {

    bool aflow
    bool vsync

    inline asmsub wait() {
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
;        repeat PCM_BLOCK_SIZE {
;            cx16.VERA_AUDIO_DATA = @(ptr)
;            ptr++
;        }
    }
}
