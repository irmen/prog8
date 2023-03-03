%import textio
%import diskio
%import cx16diskio
%import floats
%import adpcm
%import wavfile
%option no_sysinit

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

main {
    uword vera_rate_hz
    ubyte vera_rate

    sub start() {
        uword buffer = memory("buffer", 1024, 256)
        str MUSIC_FILENAME = "?"*32

        txt.print("name of .wav file to play: ")
        while 0==txt.input_chars(MUSIC_FILENAME) {
            ; until user types a name...
        }

        bool wav_ok = false
        txt.print("\nchecking ")
        txt.print(MUSIC_FILENAME)
        txt.nl()
        if diskio.f_open(8, MUSIC_FILENAME) {
            void cx16diskio.f_read(buffer, 128)
            wav_ok = wavfile.parse_header(buffer)
            diskio.f_close()
        }
        if not wav_ok {
            error("no good wav file!")
        }

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
           wavfile.sample_rate > 44100 or
           wavfile.bits_per_sample>16 {
            error("unsupported format!")
        }

        if wavfile.wavefmt==wavfile.WAVE_FORMAT_DVI_ADPCM {
            if(wavfile.block_align!=256 or wavfile.nchannels!=1) {
                error("unsupported format!")
            }
        }

        txt.print("\ngood file! playback starts!\n")
        cx16.rombank(0)                         ; activate kernal bank for faster calls
        cx16.VERA_AUDIO_RATE = 0                ; halt playback
        cx16.VERA_AUDIO_CTRL = %10101111        ; mono 16 bit
        if wavfile.nchannels==2
            cx16.VERA_AUDIO_CTRL = %10111111    ; stereo 16 bit
        if(wavfile.bits_per_sample==8)
            cx16.VERA_AUDIO_CTRL &= %11011111    ; set to 8 bit instead
        repeat 1024
            cx16.VERA_AUDIO_DATA = 0            ; fill buffer with short silence

        sys.set_irqd()
        cx16.CINV = &interrupt.handler
        cx16.VERA_IEN = %00001000               ; enable AFLOW  only for now
        sys.clear_irqd()

        if diskio.f_open(8, MUSIC_FILENAME) {
            uword block_size = 1024
            if wavfile.wavefmt==wavfile.WAVE_FORMAT_DVI_ADPCM
                block_size = wavfile.block_align
            void cx16diskio.f_read(buffer, wavfile.data_offset)       ; skip to actual sample data start
            void cx16diskio.f_read(buffer, block_size)  ; preload buffer
            cx16.VERA_AUDIO_RATE = vera_rate    ; start playback
            repeat {
                interrupt.wait_and_clear_aflow_semaphore()
                ;; cx16.vpoke(1,$fa00, $a0)    ; paint a screen color
                uword size = cx16diskio.f_read(buffer, block_size)
                ;; cx16.vpoke(1,$fa00, $00)    ; paint a screen color
                if size<block_size
                    break
                txt.chrout('.')
            }
            diskio.f_close()
        } else {
            txt.print("load error")
        }

        cx16.VERA_AUDIO_RATE = 0                ; halt playback
        txt.print("done!\n")
        repeat {
        }
    }

    sub calculate_vera_rate() {
        const float vera_freq_factor = 25e6 / 65536.0
        vera_rate = (wavfile.sample_rate as float / vera_freq_factor) + 1.0 as ubyte
        vera_rate_hz = (vera_rate as float) * vera_freq_factor as uword
    }

    sub error(str msg) {
        txt.print(msg)
        repeat {
        }
    }
}


interrupt {

    bool aflow_semaphore

    asmsub wait_and_clear_aflow_semaphore() {
        %asm {{
-           wai
            lda  aflow_semaphore
            bne  -
            inc  aflow_semaphore
            rts
        }}
    }

    sub handler() {
        if cx16.VERA_ISR & %00001000 {
            ; AFLOW irq occurred, refill buffer
            aflow_semaphore = 0
            if wavfile.wavefmt==wavfile.WAVE_FORMAT_DVI_ADPCM
                adpcm_block()
            else if wavfile.bits_per_sample==16
                uncompressed_block_16()
            else
                uncompressed_block_8()
        } else if cx16.VERA_ISR & %00000001 {
            cx16.VERA_ISR = %00000001
            ; TODO handle vsync irq
        }

        %asm {{
            ply
            plx
            pla
            rti
        }}
    }

    asmsub uncompressed_block_8() {
        ; optimized loop to put 1024 bytes of data into the fifo as fast as possible
        ; converting unsigned wav 8 bit samples to signed 8 bit on the fly
        %asm {{
            lda  main.start.buffer
            sta  cx16.r0L
            lda  main.start.buffer+1
            sta  cx16.r0H
            ldx  #4
-           ldy  #0
-           lda  (cx16.r0),y
            sec
            sbc  #128       ; convert to signed
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
;        ubyte @requirezp sample
;        repeat 1024 {
;            sample = @(ptr) - 128
;            cx16.VERA_AUDIO_DATA = sample
;            ptr++
;        }
    }

    asmsub uncompressed_block_16() {
        ; optimized loop to put 1024 bytes of data into the fifo as fast as possible
        %asm {{
            lda  main.start.buffer
            sta  cx16.r0L
            lda  main.start.buffer+1
            sta  cx16.r0H
            ldx  #4
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

    sub adpcm_block() {
        ; refill the fifo buffer with one decoded adpcm block (1010 bytes of pcm data)
        uword @requirezp nibblesptr = main.start.buffer
        adpcm.init(peekw(nibblesptr), @(nibblesptr+2))
        cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
        cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
        nibblesptr += 4
        repeat 252 {
           ubyte @zp nibble = @(nibblesptr)
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
