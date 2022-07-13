%import syslib

psg {
    ; $1F9C0 - $1F9FF 	16 blocks of 4 PSG registers (16 voices)
    ; 00  frequency word LSB
    ; 01  frequency word MSB.    freqword = HERZ / 0.3725290298461914
    ; 02  bit 7 =right, bit 6 = left, bits 5-0 = volume 0-63 levels
    ; 03  bit 7,6 = waveform, bits 5-0 = Pulse width 0-63
    ; waveform: 0=pulse, 1=sawtooth, 2=triangle, 3=noise
    const ubyte PULSE    = %00000000
    const ubyte SAWTOOTH = %01000000
    const ubyte TRIANGLE = %10000000
    const ubyte NOISE    = %11000000
    const ubyte LEFT     = %01000000
    const ubyte RIGHT    = %10000000

    sub voice(ubyte voice_num, ubyte channel, ubyte volume, ubyte waveform, ubyte pulsewidth) {
        envelope_states[voice_num] = 255
        cx16.r0 = $f9c2 + voice_num * 4
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = lsb(cx16.r0)
        cx16.VERA_ADDR_M = msb(cx16.r0)
        cx16.VERA_ADDR_H = 1
        cx16.VERA_DATA0 = channel | volume
        cx16.VERA_ADDR_L++
        cx16.VERA_DATA0 = waveform | pulsewidth
        envelope_volumes[voice_num] = mkword(volume, 0)
        envelope_maxvolumes[voice_num] = volume
    }

;    sub freq_hz(ubyte voice_num, float hertz) {
;        ; this would rely on floating point math to convert hertz to vera frequency
;        ; TODO should be replaced by integer math maybe with a lookup table?
;        uword vera_freq = (hertz / 0.3725290298461914) as uword
;        freq(voice_num, vera_freq)
;    }

    sub freq(ubyte voice_num, uword vera_freq) {
        cx16.r0 = $f9c0 + voice_num * 4
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = lsb(cx16.r0)
        cx16.VERA_ADDR_M = msb(cx16.r0)
        cx16.VERA_ADDR_H = 1
        cx16.VERA_DATA0 = lsb(vera_freq)
        cx16.VERA_ADDR_L++
        cx16.VERA_DATA0 = msb(vera_freq)
    }

    sub volume(ubyte voice_num, ubyte vol) {
        cx16.r0 = $f9c2 + voice_num * 4
        cx16.vpoke(1, cx16.r0, cx16.vpeek(1, cx16.r0) & %11000000 | vol)
        envelope_volumes[voice_num] = mkword(vol, 0)
        envelope_maxvolumes[voice_num] = vol
    }

    sub pulse_width(ubyte voice_num, ubyte pw) {
        cx16.r0 = $f9c3 + voice_num * 4
        cx16.vpoke(1, cx16.r0, cx16.vpeek(1, cx16.r0) & %11000000 | pw)
    }

    sub envelope(ubyte voice_num, ubyte maxvolume, ubyte attack, ubyte sustain, ubyte release) {
        envelope_states[voice_num] = 255
        envelope_attacks[voice_num] = attack
        envelope_sustains[voice_num] = sustain
        envelope_releases[voice_num] = release
        if attack
            attack = 0
        else
            attack = maxvolume     ; max volume when no attack is set
        envelope_volumes[voice_num] = mkword(attack, 0)
        envelope_maxvolumes[voice_num] = maxvolume
        envelope_states[voice_num] = 0
    }

    sub silent() {
        for cx16.r1L in 0 to 15 {
            envelope_states[cx16.r1L] = 255
            envelope_volumes[cx16.r1L] = 0
            volume(cx16.r1L, 0)
        }
    }

    sub envelopes_irq() {
        ; If you want to use real-time volume envelopes (Attack-Sustain-Release),
        ; you have to call this routine every 1/60th second, for example from your vsync irq handler,
        ; or just install this routine as the only irq handler if you don't have to do other things there.
        ; Example: cx16.set_irq(&psg.envelopes_irq, true)

        ; cx16.r0 = the volume word (volume scaled by 256)
        ; cx16.r1L = the voice number
        ; cx16.r2L = attack value

        pushw(cx16.r0)
        push(cx16.r1L)
        push(cx16.r2L)
        pushw(cx16.r9)
        ; calculate new volumes
        for cx16.r1L in 0 to 15 {
            when envelope_states[cx16.r1L] {
                0 -> {
                    ; attack
                    cx16.r2L = envelope_maxvolumes[cx16.r1L]
                    cx16.r0 = envelope_volumes[cx16.r1L] + envelope_attacks[cx16.r1L] * $0040
                    if msb(cx16.r0) > cx16.r2L or envelope_attacks[cx16.r1L]==0 {
                        cx16.r0 = mkword(cx16.r2L, 0)
                        envelope_attacks[cx16.r1L] = 0
                        envelope_states[cx16.r1L] = 1  ; start sustain
                    }
                    envelope_volumes[cx16.r1L] = cx16.r0
                }
                1 -> {
                    ; sustain
                    if envelope_sustains[cx16.r1L] {
                        envelope_sustains[cx16.r1L]--
                    } else {
                        envelope_states[cx16.r1L] = 2  ; start release
                    }
                }
                2 -> {
                    ; release
                    cx16.r0 = envelope_volumes[cx16.r1L] - envelope_releases[cx16.r1L] * $0040
                    if msb(cx16.r0) & %11000000 {
                        cx16.r0 = 0
                        envelope_releases[cx16.r1L] = 0
                    }
                    envelope_volumes[cx16.r1L] = cx16.r0
                }
            }
        }

        ; set new volumes of all 16 voices, using vera stride of 4
        cx16.push_vera_context()
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = $c2
        cx16.VERA_ADDR_M = $f9
        cx16.VERA_ADDR_H = 1 | %00110000
        cx16.VERA_CTRL = 1
        cx16.VERA_ADDR_L = $c2
        cx16.VERA_ADDR_M = $f9
        cx16.VERA_ADDR_H = 1 | %00110000
        for cx16.r1L in 0 to 15 {
            cx16.VERA_DATA0 = cx16.VERA_DATA1 & %11000000 | msb(envelope_volumes[cx16.r1L])
        }
        cx16.pop_vera_context()
        popw(cx16.r9)
        pop(cx16.r2L)
        pop(cx16.r1L)
        popw(cx16.r0)
    }

    ubyte[16] envelope_states
    uword[16] envelope_volumes      ; scaled by 256
    ubyte[16] envelope_attacks
    ubyte[16] envelope_sustains
    ubyte[16] envelope_releases
    ubyte[16] envelope_maxvolumes
}
