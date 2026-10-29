; Memory-mapped array of struct instances: overlays the four Paula audio channel
; register blocks at $dff0a0, so a field write becomes a hardware register access.
; The stride is the struct size, which is padded with filler fields here to match
; the hardware's 16 bytes per channel.

main {
    struct AudioChannel {
        pointer LC       ; 0 sample pointer
        uword LEN        ; 4 length in words
        uword PER        ; 6 period
        uword VOL        ; 8 volume
        word DAT         ; 10 sample data (write only)
        uword pad0       ; 12 filler, so the stride matches the hardware's $10
        uword pad1
    }

    ; overlay the four channel blocks (custom.AUD0PT/AUD1PT/AUD2PT/AUD3PT)
    &AudioChannel[4] channels = $dff0a0

    sub start() {
        channels[0].VOL = 64            ; -> $dff0a8
        channels[1].PER = 428           ; -> $dff0b6
        channels[2].LEN = 128           ; -> $dff0c4
        uword wordcount = channels[3].LEN     ; -> $dff0d4, a read works too
    }
}
