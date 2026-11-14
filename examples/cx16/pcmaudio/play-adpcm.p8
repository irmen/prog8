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
    bool restart_msg

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
        adpcm_size = lsw(wavfile.data_size)                 ; we assume the data is <64Kb so only low word is enough
        num_adpcm_blocks = (adpcm_size / 256) as ubyte      ; THE ADPCM DATA NEEDS TO BE ENCODED IN 256-byte BLOCKS !
    }

    sub decoding_report(float pcm_words_per_block) {
        const float REFRESH_RATE = 25.0e6/(525.0*800)       ; Vera VGA refresh rate is not precisely 60 hz!
        float duration_secs = (cbm.RDTIM16() as float) / REFRESH_RATE
        floats.print(duration_secs)
        txt.print(" seconds (approx)\n")
        float src_per_second = adpcm_size as float / duration_secs
        floats.print(src_per_second)
        txt.print(" adpcm data bytes/sec\n")
        float words_per_second = pcm_words_per_block * (num_adpcm_blocks as float) / duration_secs
        when wavfile.nchannels {
            1 -> {
                floats.print(words_per_second)
                txt.print(" decoded mono pcm words/sec (max hz)\n")
            }
            2 -> {
                floats.print(words_per_second)
                txt.print(" decoded pcm words/sec\n")
                floats.print(words_per_second/2)
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

        cx16.enable_irq_handlers(true)
        when wavfile.nchannels {
            1 -> {
                cx16.VERA_AUDIO_CTRL = %10101011        ; mono 16 bit, volume 11
                cx16.set_aflow_irq_handler(&mono.irq_handler)
            }
            2 -> {
                cx16.VERA_AUDIO_CTRL = %10111011        ; stereo 16 bit, volume 11
                cx16.set_aflow_irq_handler(&stereo.irq_handler)
            }
        }

        cx16.VERA_IEN = %00001000               ; enable AFLOW
        sys.clear_irqd()
        cx16.VERA_AUDIO_RATE = vera_rate        ; start playback

        txt.print("\naudio via irq\n")

        repeat {
            ; audio will play via the IRQ.
            if main.restart_msg {
                txt.print("end of data, restarting.\n")
                main.restart_msg = false
            }
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
        repeat main.num_adpcm_blocks {
            adpcm.decode_block_mono(main.nibblesptr)
            main.nibblesptr += 256
        }
    }

    sub irq_handler() -> bool {
        if cx16.VERA_ISR & %00001000 !=0 {
            ; AFLOW irq.
            ;; cx16.vpoke(1,$fa0c, $a0)    ; paint a screen color

            adpcm.decode_block_mono(main.nibblesptr)
            main.nibblesptr += 256
            main.adpcm_blocks_left--
            if main.adpcm_blocks_left==0 {
                ; restart adpcm data from the beginning
                main.nibblesptr = &wavdata.wav_data + wavfile.data_offset
                main.adpcm_blocks_left = main.num_adpcm_blocks
                main.restart_msg = true
            }

        } else {
            ; it's not AFLOW, handle other IRQ here.
        }

        ;; cx16.vpoke(1,$fa0c, 0)      ; back to other screen color

        return false
    }

}

stereo {

    sub benchmark() {
        main.nibblesptr = &wavdata.wav_data + wavfile.data_offset
        txt.print("\n\ndecoding all blocks...\n")

        repeat main.num_adpcm_blocks {
            adpcm.decode_block_stereo(main.nibblesptr)
            main.nibblesptr += 256
        }
    }

    sub irq_handler() -> bool {
        if cx16.VERA_ISR & %00001000 !=0 {
            ; AFLOW irq.
    	    ;; cx16.vpoke(1,$fa0c, $a0)    ; paint a screen color

            adpcm.decode_block_stereo(main.nibblesptr)
            main.nibblesptr += 256
            main.adpcm_blocks_left--
            if main.adpcm_blocks_left==0 {
                ; restart adpcm data from the beginning
                main.nibblesptr = &wavdata.wav_data + wavfile.data_offset
                main.adpcm_blocks_left = main.num_adpcm_blocks
                main.restart_msg = true
            }

        } else {
            ; it's not AFLOW, handle other IRQ here.
        }

        ;; cx16.vpoke(1,$fa0c, 0)      ; back to other screen color

        return false
    }

}

wavdata {

wav_data:
    %asmbinary "small-adpcm-mono.wav"
wav_data_end:

}
