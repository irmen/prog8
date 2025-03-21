; This program can stream a simple uncompressed PCM file from the sdcard.
;
; It is hardwired to play a 16 bit stereo PCM file,
; but you can type in the sample rate you want to play it in.
;
; It uses a AFLOW irq handler to refill the vera's PCM fifo buffer.
;
; The irq handler sets a flag that signals the main program to load the next block of
; data from disk.  It is problematic to call kernal I/O routines inside an irq handler,
; otherwise the aflow handler itself could have loaded the pcm data straight into
; the vera's fifo buffer.  But this could lead to race conditions so we need explicit buffering.
;
; The IRQ handlers are installed using Prog8's support routines for irq handlers.
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
            music.init()
            interrupts.setup()
            music.start()
        } else {
            txt.print("\nio error")
        }

        repeat {
            interrupts.wait()
            if interrupts.aflow {
                ; read the next 1024 bytes of audio data into the buffer
                txt.chrout('.')
                if diskio.f_read(interrupts.audio_buffer, 1024) != 1024
                    break
                interrupts.aflow = false
            }
        }
    }
}


interrupts {
    bool aflow
    uword audio_buffer = memory("audiobuffer", 1024, 0)

    sub setup() {
        aflow = false
        sys.memset(audio_buffer, 1024, 0)
        cx16.enable_irq_handlers(true)
        cx16.set_aflow_irq_handler(&aflow_handler)
        ; no other irqs in this example.
    }

    inline asmsub wait() {
        %asm {{
            wai
        }}
    }

    sub aflow_handler() -> bool {
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
_loop       lda  $ffff,y        ; modified
            sta  cx16.VERA_AUDIO_DATA
            iny
_lp2        lda  $ffff,y        ; modified
            sta  cx16.VERA_AUDIO_DATA
            iny
            bne  _loop
            inc  _loop+2
            inc  _lp2+2
            dex
            bne  _loop
        }}
        aflow = true        ; signal main program to read the next block of audio data: it is unsafe to to I/O in a irq handler!
        return false
    }
}

music {
    uword vera_rate_hz
    ubyte vera_rate

    sub init() {
        cx16.VERA_AUDIO_RATE = 0            ; halt playback
        cx16.VERA_AUDIO_CTRL = %10111011    ; stereo 16 bit, volume 11
    }

    sub start() {
        cx16.VERA_AUDIO_RATE = vera_rate    ; start playback
    }

    sub calculate_vera_rate(uword sample_rate) {
        const uword vera_freq_factor = 25_000_000 / 65536
        vera_rate = (sample_rate / vera_freq_factor) as ubyte + 1
        vera_rate_hz = vera_rate * vera_freq_factor
    }
}
