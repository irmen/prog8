%import textio
%import floats
%import wavfile
%import adpcm
%option no_sysinit
%zeropage basicsafe

;
; Simple IMA ADPCM playback example.  (factor 4 lossy compressed pcm audio)
;
; NOTE:  this program requires 16 bits MONO or STEREO audio, and 256 byte encoded block size!
; HOW TO CREATE SUCH IMA-ADPCM ENCODED AUDIO? Use sox or ffmpeg:
; $ sox --guard source.mp3 -r 8000 -c 1 -e ima-adpcm out.wav trim 01:27.50 00:09
; $ ffmpeg -i source.mp3 -ss 00:01:27.50 -to 00:01:36.50  -ar 8000 -ac 1 -c:a adpcm_ima_wav -block_size 256 -map_metadata -1 -bitexact out.wav
; Or use a tool such as https://github.com/dbry/adpcm-xq   (make sure to set correct block size)
;
; NOTE: sox may generate IMA-ADPCM files with a block size different than 256 bytes, which is not supported by this decoder. Use ffmpeg instead.
;

main {

    ubyte adpcm_blocks_left
    uword vera_rate_hz
    ubyte vera_rate
    ubyte num_adpcm_blocks
    uword adpcm_size
    uword @requirezp nibblesptr

    sub start() {
        if not wavfile.parse_header(&wavdata.wav_data) {
            txt.print("invalid wav\n")
            sys.exit(1)
        }

        calculate_vera_rate()
        calculate_adpcm_blocks()

        txt.print_ub(num_adpcm_blocks)
        txt.print(" blocks = ")
        txt.print_uw(adpcm_size)
        txt.print(" adpcm bytes\nsamplerate = ")
        txt.print_uw(wavfile.sample_rate)
        txt.print(" vera rate = ")
        txt.print_uw(vera_rate_hz)
        txt.print(" #channels = ")
        txt.print_ub(wavfile.nchannels)
        txt.print("\n\n(b)enchmark or (p)layback? ")

        when cbm.CHRIN() {
            'b' -> {
                cbm.SETTIM(0,0,0)
                when wavfile.nchannels {
                    1-> {
                        mono.benchmark()
                        decoding_report(1 + 252*2)
                    }
                    2-> {
                        stereo.benchmark()
                        decoding_report(2 + 248*4)
                    }
                }
            }
            'p' -> playback()
        }
    }

    sub calculate_vera_rate() {
        const float vera_freq_factor = 25e6 / 65536.0
        vera_rate = (wavfile.sample_rate as float / vera_freq_factor) + 1.0 as ubyte
        vera_rate_hz = (vera_rate as float) * vera_freq_factor as uword
    }

    sub calculate_adpcm_blocks() {
        adpcm_size = wavfile.data_size_lo                   ; we assume the data is <64Kb so only low word is enough
        num_adpcm_blocks = (adpcm_size / 256) as ubyte      ; THE ADPCM DATA NEEDS TO BE ENCODED IN 256-byte BLOCKS !
    }

    sub decoding_report(float pcm_words_per_block) {
        const float REFRESH_RATE = 25.0e6/(525.0*800)       ; Vera VGA refresh rate is not precisely 60 hz!
        float duration_secs = (cbm.RDTIM16() as float) / REFRESH_RATE
        floats.print(duration_secs)
        txt.print(" seconds (approx)\n")
        float src_per_second = adpcm_size as float / duration_secs
        txt.print_uw(src_per_second as uword)
        txt.print(" adpcm data bytes/sec\n")
        float words_per_second = pcm_words_per_block * (num_adpcm_blocks as float) / duration_secs
        when wavfile.nchannels {
            1 -> {
                txt.print_uw(words_per_second as uword)
                txt.print(" decoded mono pcm words/sec (max hz)\n")
            }
            2 -> {
                txt.print_uw(words_per_second as uword)
                txt.print(" decoded pcm words/sec\n")
                txt.print_uw(words_per_second/2 as uword)
                txt.print(" decoded stereo audio frames/sec (max hz)\n")
            }
        }
    }

    sub playback() {
        nibblesptr = &wavdata.wav_data + wavfile.data_offset
        adpcm_blocks_left = num_adpcm_blocks

        sys.set_irqd()
        cx16.VERA_AUDIO_RATE = 0                ; halt playback
        repeat 1024 {
            cx16.VERA_AUDIO_DATA = 0
        }

        when wavfile.nchannels {
            1 -> {
                cx16.VERA_AUDIO_CTRL = %10101011        ; mono 16 bit, volume 11
                cbm.CINV = &mono.irq_handler
            }
            2 -> {
                cx16.VERA_AUDIO_CTRL = %10111011        ; stereo 16 bit, volume 11
                cbm.CINV = &stereo.irq_handler
            }
        }

        cx16.VERA_IEN = %00001000               ; enable AFLOW
        sys.clear_irqd()
        cx16.VERA_AUDIO_RATE = vera_rate        ; start playback

        txt.print("\naudio via irq\n")

        repeat {
            ; audio will play via the IRQ.
        }

        ; not reached:
;        cx16.VERA_AUDIO_CTRL = %00100000
;        cx16.VERA_AUDIO_RATE = 0
;        txt.print("audio off.\n")
    }

}

mono {
    sub benchmark() {
        main.nibblesptr = &wavdata.wav_data + wavfile.data_offset
        txt.print("\ndecoding all blocks...\n")
        repeat main.num_adpcm_blocks
            decode_block()
    }

    sub decode_block() {
        ; refill the fifo buffer with one decoded adpcm block (1010 bytes of pcm data)
        adpcm.init(peekw(main.nibblesptr), @(main.nibblesptr+2))
        cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
        cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
        main.nibblesptr += 4
        ubyte @zp nibble
        repeat 252/2 {
            unroll 2 {
                nibble = @(main.nibblesptr)
                adpcm.decode_nibble(nibble & 15)     ; first word  (note: upper nibble needs to be zero!)
                cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
                cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
                adpcm.decode_nibble(nibble>>4)       ; second word  (note: upper nibble is zero, after the shifts.)
                cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
                cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
                main.nibblesptr++
            }
        }
    }

    sub irq_handler() {
        if cx16.VERA_ISR & %00001000 !=0 {
            ; AFLOW irq.
            ;; cx16.vpoke(1,$fa0c, $a0)    ; paint a screen color

            decode_block()
            main.adpcm_blocks_left--
            if main.adpcm_blocks_left==0 {
                ; restart adpcm data from the beginning
                main.nibblesptr = &wavdata.wav_data + wavfile.data_offset
                main.adpcm_blocks_left = main.num_adpcm_blocks
                txt.print("end of data, restarting.\n")
            }

        } else {
            ; it's not AFLOW, handle other IRQ here.
        }

        ;; cx16.vpoke(1,$fa0c, 0)      ; back to other screen color

        %asm {{
            ply
            plx
            pla
            rti
        }}
    }

}

stereo {

    sub benchmark() {
        main.nibblesptr = &wavdata.wav_data + wavfile.data_offset
        txt.print("\n\ndecoding all blocks...\n")

        repeat main.num_adpcm_blocks
            decode_block()
    }

    sub decode_block() {
        ; refill the fifo buffer with one decoded adpcm block (1010 bytes of pcm data)
        adpcm.init(peekw(main.nibblesptr), @(main.nibblesptr+2))            ; left channel
        cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
        cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
        adpcm.init_second(peekw(main.nibblesptr+4), @(main.nibblesptr+6))   ; right channel
        cx16.VERA_AUDIO_DATA = lsb(adpcm.predict_2)
        cx16.VERA_AUDIO_DATA = msb(adpcm.predict_2)
        main.nibblesptr += 8
        repeat 248/8
            decode_nibbles_unrolled()
    }

    sub decode_nibbles_unrolled() {
        ; decode 4 left channel nibbles
        ; note: when calling decode_nibble(), the upper nibble in the argument needs to be zero
        uword[8] left
        uword[8] right
        ubyte @requirezp nibble = @(main.nibblesptr)
        adpcm.decode_nibble(nibble & 15)     ; first word
        left[0] = adpcm.predict
        adpcm.decode_nibble(nibble>>4)       ; second word
        left[1] = adpcm.predict
        nibble = @(main.nibblesptr+1)
        adpcm.decode_nibble(nibble & 15)     ; first word
        left[2] = adpcm.predict
        adpcm.decode_nibble(nibble>>4)       ; second word
        left[3] = adpcm.predict
        nibble = @(main.nibblesptr+2)
        adpcm.decode_nibble(nibble & 15)     ; first word
        left[4] = adpcm.predict
        adpcm.decode_nibble(nibble>>4)       ; second word
        left[5] = adpcm.predict
        nibble = @(main.nibblesptr+3)
        adpcm.decode_nibble(nibble & 15)     ; first word
        left[6] = adpcm.predict
        adpcm.decode_nibble(nibble>>4)       ; second word
        left[7] = adpcm.predict

        ; decode 4 right channel nibbles
        nibble = @(main.nibblesptr+4)
        adpcm.decode_nibble_second(nibble & 15)     ; first word
        right[0] = adpcm.predict_2
        adpcm.decode_nibble_second(nibble>>4)       ; second word
        right[1] = adpcm.predict_2
        nibble = @(main.nibblesptr+5)
        adpcm.decode_nibble_second(nibble & 15)     ; first word
        right[2] = adpcm.predict_2
        adpcm.decode_nibble_second(nibble>>4)       ; second word
        right[3] = adpcm.predict_2
        nibble = @(main.nibblesptr+6)
        adpcm.decode_nibble_second(nibble & 15)     ; first word
        right[4] = adpcm.predict_2
        adpcm.decode_nibble_second(nibble>>4)       ; second word
        right[5] = adpcm.predict_2
        nibble = @(main.nibblesptr+7)
        adpcm.decode_nibble_second(nibble & 15)     ; first word
        right[6] = adpcm.predict_2
        adpcm.decode_nibble_second(nibble>>4)       ; second word
        right[7] = adpcm.predict_2
        main.nibblesptr += 8

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

    sub irq_handler() {
        if cx16.VERA_ISR & %00001000 !=0 {
            ; AFLOW irq.
    	    ;; cx16.vpoke(1,$fa0c, $a0)    ; paint a screen color

            decode_block()
            main.adpcm_blocks_left--
            if main.adpcm_blocks_left==0 {
                ; restart adpcm data from the beginning
                main.nibblesptr = &wavdata.wav_data + wavfile.data_offset
                main.adpcm_blocks_left = main.num_adpcm_blocks
                txt.print("end of data, restarting.\n")
            }

        } else {
            ; it's not AFLOW, handle other IRQ here.
        }

        ;; cx16.vpoke(1,$fa0c, 0)      ; back to other screen color

        %asm {{
            ply
	        plx
	        pla
	        rti
        }}
    }

}

wavdata {

wav_data:
    %asmbinary "small-adpcm-mono.wav"
wav_data_end:

}
