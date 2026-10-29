; Paula direct audio driver for Amiga custom hardware.
; Hardware-banging 4-channel 8-bit PCM playback with no OS involved.
; Use it after custom.grab_system() to own the machine (games, demos).
; All samples and streaming buffers MUST live in CHIP RAM.
;
; Channel to speaker mapping: channels 0+3 = left, 1+2 = right.
; Replay period = 3546895 / frequency (PAL clock), 124 is the practical minimum.
;
; Paula has no one-shot mode: when a channel's length counter expires it
; reloads its pointer, so a sample repeats forever. One-shot samples must
; start with a single zero word; the driver arms that reload with a pointer
; to that first word, so the sample plays once and then falls silent on the
; zero word (the same trick ptplayer uses). A finished channel asks for an
; audio interrupt on every pass of that zero word, which is how is_playing()
; and stream_ready() notice the end. The driver only polls that flag and
; never installs an interrupt handler, so it works with interrupts disabled
; and does not claim a vector.

%import custom

sound {
    %option merge, no_symbol_prefixing, ignore_unused

    ; ========== Channels and constants ==========

    enum Channel {
        C0 = 0,    ; left speaker
        C1 = 1,    ; right speaker
        C2 = 2,    ; right speaker
        C3 = 3     ; left speaker
    }

    const ubyte VOL_MAX = 64
    const ubyte VOL_MUTE = 0
    const long CLOCK_PAL = 3546895
    const uword PERIOD_MIN = 124

    ; ADKCON bit 9: set = LED lowpass filter off (bright power LED)
    const uword ADKF_LEDFILTER_OFF = $0200

    ; per channel state
    const ubyte MODE_FREE = 0
    const ubyte MODE_ONESHOT = 1
    const ubyte MODE_LOOP = 2
    const ubyte MODE_STREAM = 3

    ; ========== Driver state ==========

    private ubyte[4] chan_mode
    private ubyte[4] chan_partner      ; 14-bit partner channel, $ff when not paired
    private uword[4] chan_period       ; current period, sizes the end-of-sample check

    private pointer stream_buf0
    private pointer stream_buf1
    private uword stream_len_words
    private ubyte stream_channel = $ff     ; channel streaming, $ff when idle
    private ubyte stream_playing           ; which of the two buffers Paula plays now

    ; ========== Driver lifetime ==========

    sub init() {
        ; Take over the audio hardware: mute all channels, disable the LED
        ; lowpass filter and channel modulation. play()/loop()/stream_start()
        ; enable DMA on their channels; idle channels are silent because
        ; their volume is zero.
        for ubyte c in 0 to 3 {
            chan_mode[c] = MODE_FREE
            chan_partner[c] = $ff
            chan_period[c] = 428
            hw_set_volume(c, VOL_MUTE)
        }
        custom.DMACON = custom.DMAF_AUDIO    ; no audio DMA before a channel is started
        custom.INTREQ = custom.INTF_AUD0 | custom.INTF_AUD1 | custom.INTF_AUD2 | custom.INTF_AUD3
        custom.ADKCON = $00ff                                       ; no channel modulation, no period attach
        custom.ADKCON = custom.ADKF_SETCLR | ADKF_LEDFILTER_OFF     ; lowpass filter off
        stream_channel = $ff
        stream_playing = 0
    }

    sub start() {
        ; play()/loop()/stream_start() enable DMA on their channels
        ; automatically. This routine is kept for API compatibility.
    }

    sub shutdown() {
        ; Disable audio DMA and silence all channels.
        stop_all()
        custom.DMACON = custom.DMAF_AUDIO
    }

    ; ========== One-shot and looped playback ==========

    sub play(ubyte channel, pointer sample, uword len_words, uword period, ubyte volume) {
        ; Play an 8-bit sample once on the given channel (0-3).
        ; len_words is the sample length in words, sample must be in CHIP RAM.
        ; The first word of the sample must be zero; it becomes the silent
        ; loop point after the sample has played.
        if channel>3
            return
        stop(channel)
        if len_words==0 or sample==0
            return
        hw_start(channel, sample, len_words, period, clamp_volume(volume), true)
        chan_mode[channel] = MODE_ONESHOT
    }

    sub loop(ubyte channel, pointer sample, uword len_words, uword period, ubyte volume) {
        ; Play an 8-bit sample looped on the given channel until stop() is called.
        if channel>3
            return
        stop(channel)
        if len_words==0 or sample==0
            return
        hw_start(channel, sample, len_words, period, clamp_volume(volume), false)
        chan_mode[channel] = MODE_LOOP
    }

    sub play_14bit(ubyte hi_channel, pointer sample_hi, pointer sample_lo, uword len_words, uword period, ubyte volume) {
        ; Play a 14-bit sample using a channel pair (hi_channel carries the
        ; high 8 bits at full volume, its partner the low bits at volume 1).
        ; Both sample_hi and sample_lo must start with a zero word.
        if hi_channel>3
            return
        ubyte lo_channel = hi_channel ^ 3     ; 0+3 left, 1+2 right
        stop(hi_channel)
        stop(lo_channel)
        volume = clamp_volume(volume)
        if len_words==0 or sample_hi==0 or sample_lo==0
            return
        hw_start(hi_channel, sample_hi, len_words, period, volume, true)
        hw_start(lo_channel, sample_lo, len_words, period, 1, true)
        chan_mode[hi_channel] = MODE_ONESHOT
        chan_mode[lo_channel] = MODE_ONESHOT
        chan_partner[hi_channel] = lo_channel
        chan_partner[lo_channel] = hi_channel
    }

    sub stop(ubyte channel) {
        ; Silence the given channel and free it for reuse.
        if channel>3
            return
        detach(channel)
        hw_stop(channel)
        chan_mode[channel] = MODE_FREE
        if channel==stream_channel
            stream_channel = $ff
    }

    sub stop_all() {
        ; Silence all four channels.
        for ubyte c in 0 to 3
            stop(c)
    }

    ; ========== Live control ==========

    sub set_volume(ubyte channel, ubyte volume) {
        ; Set channel volume (0-64) while it is playing.
        if channel>3
            return
        volume = clamp_volume(volume)
        hw_set_volume(channel, volume)
        ubyte other = chan_partner[channel]
        if other!=$ff
            hw_set_volume(other, 1)     ; keep the 14-bit ratio intact
    }

    sub set_period(ubyte channel, uword period) {
        ; Set channel replay period (pitch) while it is playing.
        if channel>3
            return
        hw_set_period(channel, period)
        ubyte other = chan_partner[channel]
        if other!=$ff
            hw_set_period(other, period)             ; a 14-bit pair must stay in sync
    }

    sub is_playing(ubyte channel) -> bool {
        ; True while the channel's DMA is still running.
        if channel>3
            return false
        ubyte mode = chan_mode[channel]
        if mode==MODE_ONESHOT {
            if tail_requested(channel) {
                stop(channel)        ; the sample ended: stop looping the zero word
                return false
            }
            return true
        }
        if mode==MODE_LOOP or mode==MODE_STREAM
            return (custom.DMACONR & (custom.DMAF_AUD0 << channel))!=0
        return false
    }

    ; ========== Double-buffered streaming ==========

    sub stream_start(ubyte channel, pointer buf_a, pointer buf_b, uword len_words, uword period, ubyte volume) {
        ; Start gapless streaming on the given channel. Both buffers must be
        ; pre-filled and in CHIP RAM; refill the idle one whenever
        ; stream_ready() reports it, e.g. once per vertical blank.
        if channel>3
            return
        if stream_channel!=$ff
            stream_stop(stream_channel)
        stop(channel)
        if len_words<2 or buf_a==0 or buf_b==0
            return
        stream_buf0 = buf_a
        stream_buf1 = buf_b
        stream_len_words = len_words
        stream_channel = channel
        stream_playing = 0
        hw_start(channel, buf_a, len_words, period, clamp_volume(volume), false)
        hw_set_sample(channel, buf_b, len_words)     ; next buffer, played when buf_a ends
        chan_mode[channel] = MODE_STREAM
    }

    sub stream_ready() -> byte {
        ; Which streaming buffer needs new data: 0 or 1, -1 when neither does.
        if stream_channel==$ff
            return -1
        uword bit = custom.INTF_AUD0 << stream_channel
        if (custom.INTREQR & bit)==0
            return -1
        custom.INTREQ = bit
        byte idle = stream_playing as byte
        if stream_playing==0
            stream_playing = 1
        else
            stream_playing = 0
        ; arm the buffer that just ended: Paula picks it up when the buffer that
        ; plays now ends, so the caller has one buffer length of time to refill it
        if idle==0
            hw_set_sample(stream_channel, stream_buf0, stream_len_words)
        else
            hw_set_sample(stream_channel, stream_buf1, stream_len_words)
        return idle
    }

    sub stream_stop(ubyte channel) {
        ; Stop streaming on the given channel and silence it.
        stop(channel)
    }

    ; ========== Hardware access ==========

    private sub hw_start(ubyte channel, pointer sample, uword len_words, uword period, ubyte volume, bool one_shot) {
        ; Restart a channel from the start of its sample. The DMA bit has to go
        ; off first: a running channel only picks up a new pointer at its next
        ; reload, so restarting it any other way would finish the old sample.
        ; For one-shot samples the first word must be zero; it is used as the
        ; silent loop point after the sample ends.
        uword dmabit = custom.DMAF_AUD0 << channel
        custom.DMACON = dmabit
        hw_set_volume(channel, volume)
        hw_set_period(channel, period)
        hw_set_sample(channel, sample, len_words)
        custom.INTREQ = custom.INTF_AUD0 << channel      ; forget an earlier end-of-sample request
        custom.DMACON = custom.DMAF_SETCLR | dmabit
        if one_shot {
            ; the reload pointer and length are only used once the current pass
            ; is over, so the sample still plays in full; loop on its first
            ; (zero) word to fall silent
            hw_set_sample(channel, sample, 1)
        }
    }

    private sub hw_stop(ubyte channel) {
        custom.DMACON = custom.DMAF_AUD0 << channel
        hw_set_volume(channel, VOL_MUTE)
        custom.INTREQ = custom.INTF_AUD0 << channel
    }

    private sub hw_set_sample(ubyte channel, pointer sample, uword len_words) {
        when channel {
            0 -> {
                custom.AUD0PT = sample
                custom.AUD0LEN = len_words
            }
            1 -> {
                custom.AUD1PT = sample
                custom.AUD1LEN = len_words
            }
            2 -> {
                custom.AUD2PT = sample
                custom.AUD2LEN = len_words
            }
            else -> {
                custom.AUD3PT = sample
                custom.AUD3LEN = len_words
            }
        }
    }

    private sub hw_set_volume(ubyte channel, ubyte volume) {
        when channel {
            0 -> custom.AUD0VOL = volume
            1 -> custom.AUD1VOL = volume
            2 -> custom.AUD2VOL = volume
            else -> custom.AUD3VOL = volume
        }
    }

    private sub hw_set_period(ubyte channel, uword period) {
        if period<PERIOD_MIN
            period = PERIOD_MIN
        chan_period[channel] = period
        when channel {
            0 -> custom.AUD0PER = period
            1 -> custom.AUD1PER = period
            2 -> custom.AUD2PER = period
            else -> custom.AUD3PER = period
        }
    }

    private sub clamp_volume(ubyte volume) -> ubyte {
        if volume>VOL_MAX
            return VOL_MAX
        return volume
    }

    private sub tail_requested(ubyte channel) -> bool {
        ; True when a one-shot sample is over and Paula is looping its first
        ; (zero) word. A finished channel asks again on every pass of that one
        ; word, so a request that does not come back shortly is a leftover of
        ; the start and is discarded here.
        uword bit = custom.INTF_AUD0 << channel
        if (custom.INTREQR & bit)==0
            return false
        custom.INTREQ = bit
        uword wait = chan_period[channel] / 4 + 64
        while wait>0 {
            wait--
            if (custom.INTREQR & bit)!=0 {
                custom.INTREQ = bit
                return true
            }
        }
        return false
    }

    private sub detach(ubyte channel) {
        ; break up a 14-bit pair that the channel is part of
        ubyte other = chan_partner[channel]
        if other!=$ff {
            chan_partner[channel] = $ff
            chan_partner[other] = $ff
            hw_stop(other)
            chan_mode[other] = MODE_FREE
        }
    }
}
