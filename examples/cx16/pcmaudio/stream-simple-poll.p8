; This program can stream a simple uncompressed PCM file from the sdcard.
;
; It is hardwired to play a 16 bit stereo PCM file,
; but you can type in the sample rate you want to play it in.
;
; It uses simple polling on the AFLOW flag rather than actually using a IRQ hander,
; to see when to refill the FIFO buffer.
;
; The sample data is simply read using diskio.f_read() routine, but that uses MACPTR internally for fast loads.


%import diskio
%import textio
%option no_sysinit

main {

    sub start() {
        str MUSIC_FILENAME = "?"*32
        txt.print("what sample rate (hz) do you want to play at: ")
        void txt.input_chars(MUSIC_FILENAME)
        music.vera_rate_hz = conv.str2uword(MUSIC_FILENAME)
        if music.vera_rate_hz==0
            music.vera_rate_hz=44100
        music.calculate_vera_rate(music.vera_rate_hz)

        txt.print("\nname of raw .pcm file to play on drive 8: ")
        while 0==txt.input_chars(MUSIC_FILENAME) {
            ; until user types a name...
        }

        if diskio.f_open(MUSIC_FILENAME) {
            cx16.rombank(0)
            void diskio.fastmode(1)
            music.start()
            while music.stream() {
                txt.chrout('.')
            }
        } else {
            txt.print("\nio error")
        }
    }
}


music {
    uword audio_buffer = memory("audiobuffer", 1024, 0)

    uword vera_rate_hz
    ubyte vera_rate

    sub start() {
        cx16.VERA_AUDIO_RATE = 0            ; halt playback
        cx16.VERA_AUDIO_CTRL = %10111011    ; stereo 16 bit, volume 11
        sys.memset(audio_buffer, 1024, 0)
        cx16.VERA_AUDIO_RATE = vera_rate    ; start playback
    }

    sub calculate_vera_rate(uword sample_rate) {
        const uword vera_freq_factor = 25_000_000 / 65536
        vera_rate = (sample_rate / vera_freq_factor) as ubyte + 1
        vera_rate_hz = vera_rate * vera_freq_factor
    }

    sub stream() -> bool {
        while cx16.VERA_ISR & %00001000 == 0 {
            ; poll until AFLOW is raised ("fifo is less than 25% full, feed me more data soon!")
        }

        ; Reading directly from disk into the audio fifo is possible, but can result in hick ups.:
        ; diskio.reset_read_channel()
        ; void, cx16.r14 = cx16.MACPTR(0, &cx16.VERA_AUDIO_DATA, true)
        ; void, cx16.r15 = cx16.MACPTR(0, &cx16.VERA_AUDIO_DATA, true)
        ; return cx16.r14+cx16.r15 == 1024

        ; so instead: copy current buffer to Vera audio FIFO and immediately read the next 1 KB block from disk into the buffer.
        fill_fifo()
        return diskio.f_read(audio_buffer, 1024) == 1024
    }

    asmsub fill_fifo() {
        ; copy 1024 bytes of audio data from the buffer into vera's fifo, quickly!
        %asm {{
            lda  p8v_audio_buffer
            sta  _loop+1
            sta  _lp2+1
            lda  p8v_audio_buffer+1
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
    }
}
