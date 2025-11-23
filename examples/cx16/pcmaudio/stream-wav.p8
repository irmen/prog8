;
; This program can stream a regular .wav file from the sdcard.
; It can be uncompressed or IMA-adpcm compressed (factor 4 lossy compression).
; See the "adpcm" module source for tips how to create those files.
;
; Note that 8 bit wav files are *unsigned* values whereas Vera wants *signed* values
; so these have to be converted on the fly. 16 bit wav files are signed already.
;
; The playback is done via AFLOW irq handler that fills the audio fifo buffer
; with around 1 Kb of new audio data. (copies raw pcm data or decodes adpcm block)
; In the meantime the main program loop reads new data blocks from the wav file
; as it is being played.
;
; NOTE: stripping the wav header and just having the raw pcm data in the file
; is slightly more efficient because the data blocks are then sector-aligned on the disk
;

%import diskio
%import floats
%import adpcm
%import wavfile
%import textio
%option no_sysinit

main {

    str MUSIC_FILENAME = "?"*32
    uword vera_rate_hz
    ubyte vera_rate

    sub start() {
        diskio.fastmode(1)
        txt.print("name of .wav file to play on drive 8: ")
        while 0==txt.input_chars(MUSIC_FILENAME) {
            ; until user types a name...
        }
        prepare_music()
        txt.print("\ngood file! playback starts! ")
        cx16.rombank(0)                         ; activate kernal bank for faster calls
        interrupts.wait()
        interrupts.set_handler()
        play_stuff()
        txt.print("done!\n")
        repeat { }
    }

    sub error(str msg) {
        txt.print(msg)
        repeat { }
    }

    sub prepare_music() {
        txt.print("\nchecking ")
        txt.print(MUSIC_FILENAME)
        txt.nl()
        bool wav_ok = false
        if diskio.f_open(MUSIC_FILENAME) {
            void diskio.f_read(music.buffer, 128)
            wav_ok = wavfile.parse_header(music.buffer)
            diskio.f_close()
        }
        if not wav_ok
            error("no good wav file!")

        calculate_vera_rate()

        txt.print("wav format: ")
        txt.print_ub(wavfile.wavefmt)
        txt.print("\nchannels: ")
        txt.print_ub(wavfile.nchannels)
        txt.print("\nsample rate: ")
        txt.print_uw(wavfile.sample_rate)
        txt.print("\nbits per sample: ")
        txt.print_uw(wavfile.bits_per_sample)
        txt.print("\ndata size: ")
        txt.print_uwhex(wavfile.data_size_hi, true)
        txt.print_uwhex(wavfile.data_size_lo, false)
        txt.print("\nvera rate: ")
        txt.print_ub(vera_rate)
        txt.print(" = ")
        txt.print_uw(vera_rate_hz)
        txt.print(" hz\n")
        if wavfile.wavefmt==wavfile.WAVE_FORMAT_DVI_ADPCM {
            txt.print("adpcm block size: ")
            txt.print_uw(wavfile.block_align)
            txt.nl()
        }

        if wavfile.nchannels>2 or
           (wavfile.wavefmt!=wavfile.WAVE_FORMAT_DVI_ADPCM and wavfile.wavefmt!=wavfile.WAVE_FORMAT_PCM) or
           wavfile.sample_rate > 48828 or
           wavfile.bits_per_sample>16
                error("unsupported format!")

        if wavfile.wavefmt==wavfile.WAVE_FORMAT_DVI_ADPCM {
            if(wavfile.block_align!=256) {
                error("unsupported block alignment!")
            }
        }

        cx16.VERA_AUDIO_RATE = 0                ; halt playback
        cx16.VERA_AUDIO_CTRL = %10101011        ; mono 16 bit, volume 11
        if wavfile.nchannels==2
            cx16.VERA_AUDIO_CTRL = %10111011    ; stereo 16 bit, volume 11
        if(wavfile.bits_per_sample==8)
            cx16.VERA_AUDIO_CTRL &= %11011111    ; set to 8 bit instead
        repeat 1024
            cx16.VERA_AUDIO_DATA = 0            ; fill buffer with short silence
    }

    sub calculate_vera_rate() {
        const float vera_freq_factor = 25e6 / 65536.0
        vera_rate = (wavfile.sample_rate as float / vera_freq_factor) + 1.0 as ubyte
        vera_rate_hz = (vera_rate as float) * vera_freq_factor as uword
    }

    sub play_stuff() {
        if diskio.f_open(MUSIC_FILENAME) {
            uword block_size = 1024
            if wavfile.wavefmt==wavfile.WAVE_FORMAT_DVI_ADPCM
                block_size = wavfile.block_align * 2      ; read 2 adpcm blocks at a time (512 bytes)
            void diskio.f_read(music.buffer, wavfile.data_offset)       ; skip to actual sample data start
            music.pre_buffer(block_size)
            cx16.VERA_AUDIO_RATE = vera_rate    ; start audio playback

            str progress_chars = "-\\|/-\\|/"
            ubyte progress = 0

            repeat {
                interrupts.wait()
                if interrupts.aflow {
                    interrupts.aflow=false
                    if not music.load_next_block(block_size)
                        break
                    ; Note: copying the samples into the fifo buffer is done by the aflow interrupt handler itself.
                    txt.chrout(progress_chars[progress/2 & 7])
                    txt.chrout($9d)     ; cursor left
                    progress++
                }
            }

            diskio.f_close()
        } else {
            error("load error")
        }

        cx16.VERA_AUDIO_RATE = 0                ; halt playback
    }

}


interrupts {

    sub set_handler() {
        sys.set_irqd()
        cbm.CINV = &handler          ; irq handler for AFLOW
        cx16.VERA_IEN = %00001000    ; enable AFLOW only
        sys.clear_irqd()
    }

    bool aflow

    inline asmsub wait() {
        %asm {{
            wai
        }}
    }

    sub handler() {
        ; we only handle aflow in this example.

        if cx16.VERA_ISR & %00001000 !=0 {
            ; Filling the fifo is the only way to clear the Aflow irq.
            ; So we do this here, otherwise the aflow irq will keep triggering.
            ; Note that filling the buffer with fresh audio samples is NOT done here,
            ; but instead in the main program code that triggers on the 'aflow' being true!
            cx16.save_virtual_registers()
            music.aflow_play_block()
            cx16.restore_virtual_registers()
            aflow = true
        }

        %asm {{
            ply
            plx
            pla
            rti
        }}
    }

}


music {
    uword @requirezp nibblesptr
    uword buffer = memory("buffer", 1024, 256)

    sub pre_buffer(uword block_size) {
        ; pre-buffer first block
        void diskio.f_read(buffer, block_size)
    }

    sub aflow_play_block() {
        ; play block that is currently in the buffer
        if wavfile.wavefmt==wavfile.WAVE_FORMAT_DVI_ADPCM {
            nibblesptr = buffer
            if wavfile.nchannels==2 {
                adpcm_block_stereo()
                adpcm_block_stereo()
            }
            else {
                adpcm_block_mono()
                adpcm_block_mono()
            }
        }
        else if wavfile.bits_per_sample==16
            uncompressed_block_16()
        else
            uncompressed_block_8()
    }

    sub load_next_block(uword block_size) -> bool {
        ; read next block from disk into the buffer, for next time the irq triggers
        return diskio.f_read(buffer, block_size) == block_size
    }

    asmsub uncompressed_block_8() {
        ; copy 1024 bytes of audio data from the buffer into vera's fifo, quickly!
        ; converting unsigned wav 8 bit samples to signed 8 bit on the fly.
        %asm {{
            lda  p8v_buffer
            sta  _loop+1
            sta  _lp2+1
            lda  p8v_buffer+1
            sta  _loop+2
            sta  _lp2+2
            ldx  #4
            ldy  #0
_loop       lda  $ffff,y    ;modified
            eor  #$80       ; convert to signed
            sta  cx16.VERA_AUDIO_DATA
            iny
_lp2        lda  $ffff,y    ; modified
            eor  #$80       ; convert to signed
            sta  cx16.VERA_AUDIO_DATA
            iny
            bne  _loop
            inc  _loop+2
            inc  _lp2+2
            dex
            bne  _loop
            rts
        }}

; original prog8 code:
;        uword @requirezp ptr = main.start.buffer
;        ubyte @requirezp sample
;        repeat 1024 {
;            sample = @(ptr) - 128
;            cx16.VERA_AUDIO_DATA = sample
;            ptr++
;        }
    }

    asmsub uncompressed_block_16() {
        ; copy 1024 bytes of audio data from the buffer into vera's fifo, quickly!
        %asm {{
            lda  p8v_buffer
            sta  _loop+1
            sta  _lp2+1
            lda  p8v_buffer+1
            sta  _loop+2
            sta  _lp2+2
            ldx  #4
            ldy  #0
_loop       lda  $ffff,y    ; modified
            sta  cx16.VERA_AUDIO_DATA
            iny
_lp2        lda  $ffff,y    ; modified
            sta  cx16.VERA_AUDIO_DATA
            iny
            bne  _loop
            inc  _loop+2
            inc  _lp2+2
            dex
            bne  _loop
            rts
        }}
; original prog8 code:
;        uword @requirezp ptr = main.start.buffer
;        repeat 1024 {
;            cx16.VERA_AUDIO_DATA = @(ptr)
;            ptr++
;        }
    }

    sub adpcm_block_mono() {
        ; refill the fifo buffer with one decoded adpcm block (1010 bytes of pcm data)
        adpcm.init(peekw(nibblesptr), @(nibblesptr+2))
        cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
        cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
        nibblesptr += 4
        ubyte @zp nibble
        repeat 252/2 {
            unroll 2 {
                nibble = @(nibblesptr)
                ; note: when calling decode_nibble(), the upper nibble in the argument needs to be zero
                adpcm.decode_nibble(nibble & 15)     ; first word
                cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
                cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
                adpcm.decode_nibble(nibble>>4)       ; second word
                cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
                cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
                nibblesptr++
            }
        }
    }

    sub adpcm_block_stereo() {
        ; refill the fifo buffer with one decoded adpcm block (1010 bytes of pcm data)
        adpcm.init(peekw(nibblesptr), @(nibblesptr+2))            ; left channel
        cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
        cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
        adpcm.init_second(peekw(nibblesptr+4), @(nibblesptr+6))   ; right channel
        cx16.VERA_AUDIO_DATA = lsb(adpcm.predict_2)
        cx16.VERA_AUDIO_DATA = msb(adpcm.predict_2)
        nibblesptr += 8
        repeat 248/8
            decode_nibbles_unrolled()
    }

    sub decode_nibbles_unrolled() {
        ; decode 4 left channel nibbles
        ; note: when calling decode_nibble(), the upper nibble in the argument needs to be zero
        uword[8] left
        uword[8] right
        ubyte @requirezp nibble = @(nibblesptr)
        adpcm.decode_nibble(nibble & 15)     ; first word
        left[0] = adpcm.predict
        adpcm.decode_nibble(nibble>>4)       ; second word
        left[1] = adpcm.predict
        nibble = @(nibblesptr+1)
        adpcm.decode_nibble(nibble & 15)     ; first word
        left[2] = adpcm.predict
        adpcm.decode_nibble(nibble>>4)       ; second word
        left[3] = adpcm.predict
        nibble = @(nibblesptr+2)
        adpcm.decode_nibble(nibble & 15)     ; first word
        left[4] = adpcm.predict
        adpcm.decode_nibble(nibble>>4)       ; second word
        left[5] = adpcm.predict
        nibble = @(nibblesptr+3)
        adpcm.decode_nibble(nibble & 15)     ; first word
        left[6] = adpcm.predict
        adpcm.decode_nibble(nibble>>4)       ; second word
        left[7] = adpcm.predict

        ; decode 4 right channel nibbles
        nibble = @(nibblesptr+4)
        adpcm.decode_nibble_second(nibble & 15)     ; first word
        right[0] = adpcm.predict_2
        adpcm.decode_nibble_second(nibble>>4)       ; second word
        right[1] = adpcm.predict_2
        nibble = @(nibblesptr+5)
        adpcm.decode_nibble_second(nibble & 15)     ; first word
        right[2] = adpcm.predict_2
        adpcm.decode_nibble_second(nibble>>4)       ; second word
        right[3] = adpcm.predict_2
        nibble = @(nibblesptr+6)
        adpcm.decode_nibble_second(nibble & 15)     ; first word
        right[4] = adpcm.predict_2
        adpcm.decode_nibble_second(nibble>>4)       ; second word
        right[5] = adpcm.predict_2
        nibble = @(nibblesptr+7)
        adpcm.decode_nibble_second(nibble & 15)     ; first word
        right[6] = adpcm.predict_2
        adpcm.decode_nibble_second(nibble>>4)       ; second word
        right[7] = adpcm.predict_2
        nibblesptr += 8

        %asm {{
            ; copy to vera PSG fifo buffer
            ldy  #0
-           lda  p8v_left_lsb,y
            sta  cx16.VERA_AUDIO_DATA
            lda  p8v_left_msb,y
            sta  cx16.VERA_AUDIO_DATA
            lda  p8v_right_lsb,y
            sta  cx16.VERA_AUDIO_DATA
            lda  p8v_right_msb,y
            sta  cx16.VERA_AUDIO_DATA
            iny
            cpy  #8
            bne  -
        }}
    }

}
