%import math

sfxgen {
    ; Generate and transform 8-bit signed mono audio sample data in memory.
    ; All routines operate on a caller-provided buffer (^^byte + length), which is
    ; exactly what audio.play() (Amiga) or other playback paths expect.
    ; Pure Prog8; works on every target (6502, 65C02, m68k, virtual, ...).
    ;
    ; Samples are stored as raw bytes; the signed 8-bit value is the bit-identical
    ; (re)interpretation of that byte, so reads use `as byte` and writes use `as ubyte`.

    %option no_symbol_prefixing, ignore_unused

    ; ---- helpers ----

    sub freq_to_step(uword freq_hz, uword sample_rate) -> uword {
        ; Convert a frequency (Hz) and sample rate (Hz) into the 8.8 fixed-point
        ; per-sample phase increment used by the waveform generators.
        long num = (freq_hz as long) * 256
        return (num / (sample_rate as long)) as uword
    }

    ; ---- internal segment generator (used by the presets) ----

    ; fill the sub-range [start, start+seglen) of buf with a square wave
    ; duty_pct is the high-time percentage 0..100 (50 = symmetric).
    private sub square_seg(^^byte buf, uword start, uword seglen, uword step, ubyte duty_pct) {
        ubyte threshold = ((duty_pct as uword) * 255 / 100) as ubyte
        uword phase = 0
        for uword k in 0 to seglen-1 {
            ubyte ph = (phase >> 8) as ubyte
            byte s
            if ph < threshold {
                s = 127
            } else {
                s = -128
            }
            buf[start + k] = s as ubyte
            phase += step
        }
    }

    ; ---- waveform generators (fill the whole buffer) ----

    ; fill the whole buffer with a sine wave (phase accumulator, step = 8.8 phase increment).
    sub sine(^^byte buf, uword length, uword step) {
        uword phase = 0
        for uword i in 0 to length-1 {
            buf[i] = math.sin8((phase >> 8) as ubyte) as ubyte
            phase += step
        }
    }

    ; fill the whole buffer with a square wave; duty_pct is the high-time percentage (0..100).
    sub square(^^byte buf, uword length, uword step, ubyte duty_pct) {
        uword phase = 0
        ubyte threshold = ((duty_pct as uword) * 255 / 100) as ubyte
        for uword i in 0 to length-1 {
            ubyte ph = (phase >> 8) as ubyte
            byte s
            if ph < threshold {
                s = 127
            } else {
                s = -128
            }
            buf[i] = s as ubyte
            phase += step
        }
    }

    ; fill the whole buffer with a triangle wave.
    sub triangle(^^byte buf, uword length, uword step) {
        uword phase = 0
        for uword i in 0 to length-1 {
            ubyte ph = (phase >> 8) as ubyte
            byte s
            if ph < 128 {
                s = clamp((ph as word) * 2 - 128, -128, 127) as byte
            } else {
                s = clamp(127 - ((ph as word) - 128) * 2, -128, 127) as byte
            }
            buf[i] = s as ubyte
            phase += step
        }
    }

    ; fill the whole buffer with a sawtooth wave (ramps -128..127 per period, centered).
    sub sawtooth(^^byte buf, uword length, uword step) {
        uword phase = 0
        for uword i in 0 to length-1 {
            ; (phase>>8) is 0..255 unsigned; offset by 128 to get signed -128..127 monotonic ramp
            buf[i] = (((phase >> 8) as ubyte) + 128) as ubyte
            phase += step
        }
    }

    ; fill the whole buffer with white noise (values 0..255, i.e. signed -128..127 when read).
    sub noise(^^byte buf, uword length) {
        for uword i in 0 to length-1 {
            buf[i] = math.rnd()
        }
    }

    ; fill the whole buffer with zeros (silence).
    sub silence(^^byte buf, uword length) {
        for uword i in 0 to length-1 {
            buf[i] = 0
        }
    }

    ; ---- swept waveform generators ----

    ; fill the whole buffer with a sine wave whose pitch sweeps linearly from step_start to step_end.
    sub sine_sweep(^^byte buf, uword length, uword step_start, uword step_end) {
        uword phase = 0
        for uword i in 0 to length-1 {
            buf[i] = math.sin8((phase >> 8) as ubyte) as ubyte
            uword step = step_start + (((step_end - step_start) as long) * (i as long) / (length as long)) as uword
            phase += step
        }
    }

    ; fill the whole buffer with a square wave whose pitch sweeps linearly from step_start to step_end.
    sub square_sweep(^^byte buf, uword length, uword step_start, uword step_end, ubyte duty_pct) {
        uword phase = 0
        ubyte threshold = ((duty_pct as uword) * 255 / 100) as ubyte
        for uword i in 0 to length-1 {
            ubyte ph = (phase >> 8) as ubyte
            byte s
            if ph < threshold {
                s = 127
            } else {
                s = -128
            }
            buf[i] = s as ubyte
            uword step = step_start + (((step_end - step_start) as long) * (i as long) / (length as long)) as uword
            phase += step
        }
    }

    ; ---- in-place modifiers ----

    sub adsr(^^byte buf, uword length, uword attack, uword decay, ubyte sustain_level, uword release) {
        ; apply an attack/decay/sustain/release gain envelope (sample counts).
        ; a region of length 0 is treated as "instant".
        ; clamp release_start to avoid uword underflow when release > length
        uword release_start
        if release >= length
            release_start = 0
        else
            release_start = length - release
        ; sustain_start may overflow uword, clamp to length
        long sustain_long = (attack as long) + (decay as long)
        uword sustain_start
        if sustain_long > length
            sustain_start = length
        else
            sustain_start = sustain_long as uword
        for uword i in 0 to length-1 {
            word g
            word t
            if attack > 0 and i < attack {
                g = ((i as long) * 256 / (attack as long)) as word
            } else if decay > 0 and i < sustain_start {
                t = (i - attack) as word
                g = 255 - (((255 - sustain_level) as word) * t / (decay as word))
            } else if release > 0 and i >= release_start {
                t = (length - i) as word
                g = ((sustain_level as word) * t / (release as word))
            } else {
                g = sustain_level as word
            }
            byte s = buf[i] as byte
            buf[i] = clamp((s as word) * g / 128, -128, 127) as ubyte
        }
    }

    sub amplify(^^byte buf, uword length, word gain_pct) {
        ; scale amplitude by gain_pct percent (e.g. 150 = 1.5x), clipped.
        for uword i in 0 to length-1 {
            byte s = buf[i] as byte
            buf[i] = clamp((s as word) * gain_pct / 100, -128, 127) as ubyte
        }
    }

    sub distort(^^byte buf, uword length, ubyte threshold) {
        ; hard-clip symmetric around zero at the given threshold.
        ; threshold is 0..255 but clipping is only meaningful up to 127 (signed max)
        if threshold > 127
            threshold = 127
        byte hi = threshold as byte
        byte lo = -(threshold as byte)
        for uword i in 0 to length-1 {
            byte v = buf[i] as byte
            if v > hi {
                v = hi
            } else if v < lo {
                v = lo
            }
            buf[i] = v as ubyte
        }
    }

    sub lowpass(^^byte buf, uword length, ubyte strength) {
        ; one-pole low-pass (leaky integrator). strength 0/1 = pass-through (no smoothing);
        ; higher strength = stronger smoothing / lower cutoff.
        if strength <= 1
            return
        byte prev = 0
        for uword i in 0 to length-1 {
            byte cur = buf[i] as byte
            word smoothed = ((prev as word) * (strength - 1) + (cur as word)) / strength
            prev = clamp(smoothed, -128, 127) as byte
            buf[i] = prev as ubyte
        }
    }

    ; deprecated alias for backwards compatibility
    sub lowpass_smooth(^^byte buf, uword length, ubyte strength) {
        lowpass(buf, length, strength)
    }

    sub highpass(^^byte buf, uword length, ubyte strength) {
        ; one-pole high-pass. strength 0 = pass-through; higher strength = more
        ; high-pass (removes low frequencies, keeps transients/clicks).
        ; Implements y[n] = alpha*(y[n-1] + x[n] - x[n-1]) with alpha=strength/255
        if strength == 0
            return
        byte x_prev = 0
        word y_prev = 0
        for uword i in 0 to length-1 {
            byte x = buf[i] as byte
            word hp = y_prev + (x as word) - (x_prev as word)
            ; scale by strength/256 as alpha (255 = max high-pass)
            hp = ((hp as long) * strength / 255) as word
            y_prev = clamp(hp, -128, 127)
            buf[i] = y_prev as ubyte
            x_prev = x
        }
    }

    sub echo(^^byte buf, uword length, uword delay, ubyte feedback, ubyte mix) {
        ; add a delayed, attenuated copy of the buffer onto itself (in place).
        ; feedback and mix are 0..255 attenuation factors.
        if delay == 0 or delay >= length
            return
        for uword i in delay to length-1 {
            byte delayed = buf[i - delay] as byte
            long contrib = (delayed as long) * feedback * mix
            word v = (buf[i] as byte as word) + (contrib / 65536) as word
            buf[i] = clamp(v, -128, 127) as ubyte
        }
    }

    sub reverb(^^byte dst, ^^byte src, uword length, ubyte ir_len, ubyte decay) {
        ; Convolution reverb: each output sample is the dry signal convolved with a
        ; short, synthesized impulse response (random, exponentially decaying tail).
        ; dst and src must be DIFFERENT buffers. decay is the per-tap attenuation
        ; fraction 0..255 (higher = longer, denser reverb tail, 255 = no decay).
        for uword n in 0 to length-1 {
            long sum = 0
            ubyte frac = 255
            for ubyte k in 0 to ir_len-1 {
                if n >= k {
                    byte s = src[n - k] as byte
                    sum += (s as long) * (frac as long) / 255
                }
                frac = ((frac as uword) * (decay as uword) / 255) as ubyte
            }
            dst[n] = clamp(sum as word, -128, 127) as ubyte
        }
    }

    sub vibrato(^^byte dst, ^^byte src, uword length, ubyte depth, uword rate) {
        ; pitch-wobble a source buffer into a (separate) destination buffer.
        ; dst must NOT be the same buffer as src. depth (0..255) is the maximum
        ; sample offset; rate controls the wobble speed.
        uword phase = 0
        for uword i in 0 to length-1 {
            word wobble = ((math.sin8((phase >> 8) as ubyte) as word) * depth) / 256
            long idx = (i as long) + wobble
            if idx < 0
                idx = 0
            if idx >= length
                idx = (length - 1) as long
            byte s = src[idx as uword] as byte
            dst[i] = s as ubyte
            phase += rate
        }
    }

    ; ---- mixing ----

    sub mix(^^byte dst, ^^byte src, uword src_len, word gain_pct) {
        ; additively mix src*gain_pct% into dst (clipped).
        for uword i in 0 to src_len-1 {
            byte d = dst[i] as byte
            byte s = src[i] as byte
            word s_scaled = (s as word) * gain_pct / 100
            word v = (d as word) + s_scaled
            dst[i] = clamp(v, -128, 127) as ubyte
        }
    }

    ; ---- generic sfxr-style synthesizer ----

    ; synth() is an integer, buffer-free reimplementation of the classic sfxr
    ; algorithm. Create a parameter block with:
    ;   ^^sfxgen.SynthParams p = []
    ; then fill the fields and call synth(buf, length, p).
    ; It deliberately omits the phaser/flanger (needs a ring buffer),
    ; filter resonance/ramp, delta-slide and repeat to keep the fixed-point
    ; math simple and fast.
    ; Based on the original sfxr C code:
    ;   https://github.com/grimfang4/sfxr/blob/master/sfxr/source/main.cpp
    ; Scaling notes: frequencies use period = 6502500/(freq*freq+65) (original sfxr
    ; float scaled by ~44100*?); duty is Q15 (32768=100%); envelope times are
    ; param*100000/65025 squared; vibrato increment is vib_speed^2*104/65025.

    struct SynthParams {
        ubyte wave_type       ; 0=square, 1=sawtooth, 2=sine, 3=noise
        ubyte env_attack      ; attack time
        ubyte env_sustain     ; sustain time
        ubyte env_punch       ; sustain punch amount
        ubyte env_decay       ; decay time
        ubyte base_freq       ; start frequency
        ubyte freq_limit      ; minimum frequency, 0 = no limit
        byte  freq_ramp       ; pitch slide (signed)
        ubyte vib_strength    ; vibrato depth
        ubyte vib_speed       ; vibrato speed
        byte  arp_mod         ; arpeggio multiplier (signed)
        ubyte arp_speed       ; arpeggio delay
        ubyte duty            ; square duty
        byte  duty_ramp       ; duty slide (signed)
        ubyte lpf_freq        ; low-pass amount, 255 = brightest
        ubyte hpf_freq        ; high-pass amount, 0 = none
        ubyte sound_vol       ; output volume
    }

    sub synth_defaults(^^SynthParams p) {
        ; sfxr neutral defaults - call this first, then tweak fields.
        p.wave_type = 0
        p.env_attack = 0
        p.env_sustain = 77      ; ~0.3
        p.env_punch = 0
        p.env_decay = 102       ; ~0.4
        p.base_freq = 77        ; ~0.3
        p.freq_limit = 0
        p.freq_ramp = 0
        p.vib_strength = 0
        p.vib_speed = 0
        p.arp_mod = 0
        p.arp_speed = 0
        p.duty = 0
        p.duty_ramp = 0
        p.lpf_freq = 255        ; low-pass off
        p.hpf_freq = 0
        p.sound_vol = 128       ; ~0.5
    }

    private sub env_samples(ubyte param) -> uword {
        ; map a 0..255 sfxr envelope parameter to a sample count, clamped to uword
        long b = (param as long) * 100000 / 65025
        long v = (param as long) * b
        if v > 65535
            return 65535
        return v as uword
    }

    sub synth(^^byte buf, uword length, ^^SynthParams p) {
        ; Generate a signed 8-bit mono sound from a SynthParams block.
        ; No extra buffers are allocated; filters are applied as a post-process.
        if length == 0
            return

        silence(buf, length)

        uword[3] env_len
        env_len[0] = env_samples(p.env_attack)
        env_len[1] = env_samples(p.env_sustain)
        env_len[2] = env_samples(p.env_decay)

        long period = 6502500 / ((p.base_freq as uword) * p.base_freq + 65)
        long maxperiod
        bool limit_freq = p.freq_limit > 0
        if limit_freq
            maxperiod = 6502500 / ((p.freq_limit as uword) * p.freq_limit + 65)
        else
            maxperiod = 2147483647

        long framp = p.freq_ramp as long

        uword vib_phase = 0
        ubyte vib_inc = 0
        if p.vib_strength > 0 and p.vib_speed > 0
            vib_inc = ((p.vib_speed as long) * p.vib_speed * 104 / 65025) as ubyte
        ubyte vib_amp = p.vib_strength >> 1

        long arp_factor = 16777216
        uword arp_limit = 0
        if p.arp_speed != 255 {
            ubyte am
            if p.arp_mod >= 0 {
                am = p.arp_mod as ubyte
                arp_factor = 16777216 - ((am as long) * am * 23217 / 100)
            } else {
                am = (-p.arp_mod) as ubyte
                arp_factor = 16777216 + ((am as long) * am * 2580)
            }
            ubyte t = 255 - p.arp_speed
            arp_limit = ((t as long) * t * 20000 / 65025 + 32) as uword
        }

        long duty_q = 32768 - ((p.duty as long) * 32768 / 255)
        long duty_slide = -(p.duty_ramp as long) * 4

        word gain_q = ((p.sound_vol as long) * 512 / 255) as word

        uword phase = 0
        ubyte env_stage = 0
        uword env_t = 0
        uword arp_elapsed = 0
        uword generated = length

        for uword i in 0 to length-1 {
            if arp_limit != 0 {
                arp_elapsed++
                if arp_elapsed >= arp_limit {
                    arp_limit = 0
                    ; arp_factor is Q24; split the multiply to avoid 32-bit overflow
                    period = ((period * (arp_factor >> 12)) >> 12) + ((period * (arp_factor & 4095)) >> 24)
                }
            }

            period = period - ((period * framp) >> 16)
            if period > maxperiod {
                period = maxperiod
                if limit_freq {
                    generated = i
                    break
                }
            }

            long rperiod = period
            if vib_amp > 0 {
                vib_phase += vib_inc as uword
                byte sv = math.sin8((vib_phase >> 8) as ubyte) as byte
                long vib = (sv as long) * vib_amp / 128
                rperiod = period + ((period * vib) >> 8)
            }

            long out_period = rperiod >> 3
            if out_period < 1
                out_period = 1

            duty_q += duty_slide
            if duty_q < 0 duty_q = 0
            if duty_q > 32768 duty_q = 32768

            env_t++
            if env_t > env_len[env_stage] {
                env_t = 0
                env_stage++
                if env_stage == 3 {
                    generated = i
                    break
                }
            }
            word env_vol
            if env_stage == 0 {
                if env_len[0] == 0
                    env_vol = 256
                else
                    env_vol = ((env_t as long) * 256 / env_len[0]) as word
            } else if env_stage == 1 {
                if env_len[1] == 0
                    env_vol = 256
                else {
                    long one_minus = ((env_len[1] - env_t) as long) * 256 / env_len[1]
                    env_vol = (256 + one_minus * 2 * p.env_punch / 255) as word
                }
            } else {
                if env_len[2] == 0
                    env_vol = 0
                else
                    env_vol = (256 - ((env_t as long) * 256 / env_len[2])) as word
            }

            phase++
            if phase >= (out_period as uword)
                phase = 0
            long sample
            if p.wave_type == 0 {
                ; avoid 32-bit overflow: duty_q(0..32768)*out_period can exceed 2^31
                ; compute (duty_q*out_period)>>16 as (high*Q + low*Q>>16)
                long hi = (out_period >> 16) * duty_q
                long lo = (out_period & 65535) * duty_q >> 16
                long threshold = hi + lo
                if (phase as long) < threshold
                    sample = 128
                else
                    sample = -128
            } else if p.wave_type == 1 {
                sample = 256 - ((phase as long) * 512 / out_period)
            } else if p.wave_type == 2 {
                ubyte ang = ((phase as long) * 256 / out_period) as ubyte
                sample = (math.sin8(ang) as word) * 2
            } else {
                sample = ((math.rnd() as word) - 128) * 2
            }

            long raw = sample * env_vol * gain_q
            word val = (raw / 131072) as word
            buf[i] = clamp(val, -128, 127) as ubyte
        }

        if p.lpf_freq < 255 {
            word strength = (256 - p.lpf_freq) as word
            if strength > 255
                strength = 255
            lowpass(buf, generated, strength as ubyte)
        }
        if p.hpf_freq > 0
            highpass(buf, generated, p.hpf_freq)
    }

    ; ---- composite presets (each fills a buffer with a ready-made effect) ----

    private sub preset_short_sweep(^^byte buf, uword length, uword sample_rate, uword f0, uword f1, bool square) {
        if length == 0
            return
        uword dur = sample_rate / 64
        if length < dur
            dur = length
        if square
            square_sweep(buf, dur, freq_to_step(f0, sample_rate), freq_to_step(f1, sample_rate), 50)
        else
            sine_sweep(buf, dur, freq_to_step(f0, sample_rate), freq_to_step(f1, sample_rate))
        adsr(buf, dur, dur / 32, dur - dur / 32, 0, 0)
        if dur < length {
            for uword i in dur to length-1 {
                buf[i] = 0
            }
        }
    }

    sub preset_laser(^^byte buf, uword length, uword freq_start, uword freq_end, uword sample_rate) {
        ; descending (or ascending) pitch sweep with fast decay - the classic "pew".
        sine_sweep(buf, length, freq_to_step(freq_start, sample_rate), freq_to_step(freq_end, sample_rate))
        adsr(buf, length, length / 16, length / 8, 0, length / 4)
    }

    sub preset_zap(^^byte buf, uword length, uword freq_start, uword freq_end, uword sample_rate) {
        ; square sweep with a stronger envelope - a harsher "zzap".
        square_sweep(buf, length, freq_to_step(freq_start, sample_rate), freq_to_step(freq_end, sample_rate), 50)
        adsr(buf, length, length / 16, length / 6, 0, length / 4)
    }

    sub preset_explosion(^^byte buf, uword length, uword sample_rate) {
        ; white noise, lightly smoothed (low-pass), with a long decay.
        noise(buf, length)
        lowpass(buf, length, 6)
        adsr(buf, length, length / 32, length / 4, 0, length / 3)
    }

    sub preset_blip(^^byte buf, uword length, uword freq, uword sample_rate) {
        ; short tone with a quick attack/decay (cycles removed - use length to control duration).
        sine(buf, length, freq_to_step(freq, sample_rate))
        adsr(buf, length, length / 8, length / 4, 0, length / 4)
    }

    sub preset_coin(^^byte buf, uword length, uword freq_lo, uword freq_hi, uword sample_rate) {
        ; two-tone "pickup": low note then a quick hop to a higher note.
        uword half = length / 2
        uword step_lo = freq_to_step(freq_lo, sample_rate)
        uword step_hi = freq_to_step(freq_hi, sample_rate)
        sine(buf, half, step_lo)
        uword phase = 0
        for uword i in half to length-1 {
            buf[i] = math.sin8((phase >> 8) as ubyte) as ubyte
            phase += step_hi
        }
        adsr(buf, length, length / 16, length / 8, 180, length / 8)
    }

    sub preset_jump(^^byte buf, uword length, uword freq_lo, uword freq_hi, uword sample_rate) {
        ; ascending pitch sweep - the classic "boing" jump.
        square_sweep(buf, length, freq_to_step(freq_lo, sample_rate), freq_to_step(freq_hi, sample_rate), 50)
        adsr(buf, length, length / 24, length / 8, 0, length / 8)
    }

    sub preset_powerup(^^byte buf, uword length, uword base_freq, uword sample_rate) {
        ; ascending arpeggio (root, major third, fifth, octave) - the "power up".
        uword seg = length / 4
        square_seg(buf, 0, seg, freq_to_step(base_freq, sample_rate), 50)
        square_seg(buf, seg, seg, freq_to_step((base_freq as long) * 5 / 4 as uword, sample_rate), 50)
        square_seg(buf, seg * 2, seg, freq_to_step((base_freq as long) * 3 / 2 as uword, sample_rate), 50)
        square_seg(buf, seg * 3, length - seg * 3, freq_to_step((base_freq as long) * 2 as uword, sample_rate), 50)
        adsr(buf, length, length / 24, length / 8, 200, length / 8)
    }

    sub preset_hurt(^^byte buf, uword length, uword freq_hi, uword freq_lo, uword sample_rate) {
        ; descending tone with a crunchy noise overlay - a "damage" hit.
        square_sweep(buf, length, freq_to_step(freq_hi, sample_rate), freq_to_step(freq_lo, sample_rate), 50)
        uword noiselen = length / 3
        for uword i in 0 to noiselen-1 {
            byte s = buf[i] as byte
            byte n = (math.rnd() as byte)
            buf[i] = clamp((s as word) + (n as word) / 2, -128, 127) as ubyte
        }
        adsr(buf, length, length / 32, length / 8, 0, length / 4)
    }

    sub preset_gameover(^^byte buf, uword length, uword base_freq, uword sample_rate) {
        ; descending four-note sequence - the sad "game over".
        uword seg = length / 4
        square_seg(buf, 0, seg, freq_to_step(base_freq, sample_rate), 50)
        square_seg(buf, seg, seg, freq_to_step((base_freq as long) * 7 / 8 as uword, sample_rate), 50)
        square_seg(buf, seg * 2, seg, freq_to_step((base_freq as long) * 3 / 4 as uword, sample_rate), 50)
        square_seg(buf, seg * 3, length - seg * 3, freq_to_step((base_freq as long) * 5 / 8 as uword, sample_rate), 50)
        adsr(buf, length, length / 32, length / 8, 180, length / 4)
    }

    sub preset_oneup(^^byte buf, uword length, uword base_freq, uword sample_rate) {
        ; fast ascending major arpeggio (root, major third, fifth, octave) - the "1-up" jingle.
        uword seg = length / 4
        square_seg(buf, 0, seg, freq_to_step(base_freq, sample_rate), 50)
        square_seg(buf, seg, seg, freq_to_step((base_freq as long) * 5 / 4 as uword, sample_rate), 50)
        square_seg(buf, seg * 2, seg, freq_to_step((base_freq as long) * 3 / 2 as uword, sample_rate), 50)
        square_seg(buf, seg * 3, length - seg * 3, freq_to_step((base_freq as long) * 2 as uword, sample_rate), 50)
        adsr(buf, length, length / 24, length / 8, 200, length / 8)
    }

    sub preset_select(^^byte buf, uword length, uword freq, uword sample_rate) {
        ; short, bright "menu move" blip (high square tone, very quick envelope).
        square(buf, length, freq_to_step(freq, sample_rate), 50)
        adsr(buf, length, length / 12, length / 12, 0, length / 6)
    }

    sub preset_thud(^^byte buf, uword length, uword freq_hi, uword freq_lo, uword sample_rate) {
        ; low descending tone with a fast decay - a soft "thud"/landing.
        square_sweep(buf, length, freq_to_step(freq_hi, sample_rate), freq_to_step(freq_lo, sample_rate), 50)
        adsr(buf, length, length / 32, length / 6, 0, length / 3)
    }

    ; ---- short UI / menu sound effects (each is a short burst; the rest of the
    ;      buffer is left as silence so they stay very short regardless of length) ----

    sub preset_menu_open(^^byte buf, uword length, uword sample_rate) {
        preset_short_sweep(buf, length, sample_rate, 350, 1050, false)
    }

    sub preset_menu_close(^^byte buf, uword length, uword sample_rate) {
        preset_short_sweep(buf, length, sample_rate, 1050, 350, false)
    }

    sub preset_accept(^^byte buf, uword length, uword sample_rate) {
        preset_short_sweep(buf, length, sample_rate, 523, 1046, false)
    }

    sub preset_deny(^^byte buf, uword length, uword sample_rate) {
        preset_short_sweep(buf, length, sample_rate, 400, 120, true)
    }

    sub preset_gunshot(^^byte buf, uword length, uword sample_rate) {
        ; short, sharp "crack" - a bright, fast-decaying noise burst.
        ; distinct from preset_explosion, which is longer and low-pass filtered
        ; into a rumbling boom. This has an instant attack and is gone quickly.
        if length == 0
            return
        uword dur = sample_rate / 40
        if length < dur
            dur = length
        noise(buf, dur)
        lowpass(buf, dur, 2)
        adsr(buf, dur, dur / 64, dur - dur / 64, 0, 0)
        if dur < length {
            for uword i in dur to length-1 {
                buf[i] = 0
            }
        }
    }
}
