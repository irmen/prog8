%import syslib

psg {
    %option ignore_unused

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
    const ubyte DISABLED = %11111111

    sub init() {
        ; -- initializes the psg module (all 16 voices set to disabled)
        for cx16.r1L in 0 to 15
            voice_enabled[cx16.r1L] = false
    }

    sub voice(ubyte voice_num, ubyte channel, ubyte vol, ubyte waveform, ubyte pulsewidth) {
        ; -- Enables a 'voice' on the PSG.
        ;    voice_num = 0-15, the voice number.
        ;    channel = either LEFT or RIGHT or (LEFT|RIGHT). Specifies the stereo channel(s) to use.  DISABLED=disable the voice.
        ;    vol = 0-63, the starting volume for the voice
        ;    waveform = one of PULSE,SAWTOOTH,TRIANGLE,NOISE.
        ;    pulsewidth = 0-63.  Specifies the pulse width for waveform=PULSE.
        voice_enabled[voice_num] = false
        envelope_states[voice_num] = 255
        sys.irqsafe_set_irqd()
        cx16.r0 = $f9c2 + voice_num * 4
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = lsb(cx16.r0)
        cx16.VERA_ADDR_M = msb(cx16.r0)
        cx16.VERA_ADDR_H = 1
        if channel!=DISABLED {
            cx16.VERA_DATA0 = channel | vol
            cx16.VERA_ADDR_L++
            cx16.VERA_DATA0 = waveform | pulsewidth
            envelope_volumes[voice_num] = mkword(vol, 0)
            envelope_maxvolumes[voice_num] = vol
            voice_enabled[voice_num] = true
        } else {
            cx16.VERA_DATA0 = 0
            envelope_volumes[voice_num] = 0
            envelope_maxvolumes[voice_num] = 0
            voice_enabled[voice_num] = false
        }
        sys.irqsafe_clear_irqd()
    }

;    sub freq_hz(ubyte voice_num, float hertz) {
;        ; this would rely on floating point math to convert hertz to vera frequency
;        ; could be replaced by integer math maybe with a lookup table?
;        uword vera_freq = (hertz / 0.3725290298461914) as uword
;        freq(voice_num, vera_freq)
;    }

    sub freq(ubyte voice_num, uword vera_freq) {
        ; -- Changes the frequency of the voice's sound.
        ;    voice_num = 0-15,  vera_freq = 0-65535  calculate this via the formula given in the Vera's PSG documentation.
        ;    (https://github.com/X16Community/x16-docs/blob/101759f3bfa5e6cce4e8c5a0b67cb0f2f1c6341e/X16%20Reference%20-%2009%20-%20VERA%20Programmer's%20Reference.md)
        ;    Write freq MSB first and then LSB to reduce the chance on clicks
        sys.irqsafe_set_irqd()
        cx16.r0 = $f9c1 + voice_num * 4
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = lsb(cx16.r0)
        cx16.VERA_ADDR_M = msb(cx16.r0)
        cx16.VERA_ADDR_H = 1
        cx16.VERA_DATA0 = msb(vera_freq)
        cx16.VERA_ADDR_L--
        cx16.VERA_DATA0 = lsb(vera_freq)
        sys.irqsafe_clear_irqd()
    }

    sub volume(ubyte voice_num, ubyte vol) {
        ; -- Modifies the volume of this voice.
        ;    voice_num = 0-15, vol = 0-63 where 0=silent, 63=loudest.
        envelope_volumes[voice_num] = mkword(vol, 0)
        cx16.vpoke_mask(1, $f9c2 + voice_num * 4, %11000000, vol)
        envelope_maxvolumes[voice_num] = vol
    }

    sub pulse_width(ubyte voice_num, ubyte pw) {
        ; -- Modifies the pulse width of this voice (when waveform=PULSE)
        ;    voice_num = 0-15, pw = 0-63  where 0=narrow, 63=50%cycle so square wave.
        ;    When Waveform is TRIANGLE or SAWTOOTH, it sets the XOR mode parameter instead.
        ;    (see the Vera reference manual for the exact description of this)
        cx16.vpoke_mask(1, $f9c3 + voice_num * 4, %11000000, pw)
    }

    sub envelope(ubyte voice_num, ubyte maxvolume, ubyte attack, ubyte sustain, ubyte release) {
        ; -- Enables AttackSustainRelease volume envelope for a voice.
        ;    Note: this requires setting up envelopes_irq() as well, read its description.
        ;    voice_num = 0-15   maxvolume = 0-63
        ;    attack, sustain, release = 0-255 that determine the speed of the A/D/R:
        ;    attack time:    MAXVOL/15/attack  seconds.    higher value = faster attack.
        ;    sustain time:   sustain/60 seconds    higher sustain value = longer sustain (!).
        ;    release time:   MAXVOL/15/release seconds.   higher vaule = faster release.

        envelope_states[voice_num] = 255
        envelope_attacks[voice_num] = attack
        envelope_sustains[voice_num] = sustain
        envelope_releases[voice_num] = release
        cx16.r0 = mkword(maxvolume, 0)
        if cx16.r0<envelope_volumes[voice_num]
            envelope_volumes[voice_num] = cx16.r0
        envelope_maxvolumes[voice_num] = maxvolume
        envelope_states[voice_num] = 0
    }

    sub silent() {
        ; -- Silence all active PSG voices.
        for cx16.r1L in 0 to 15 {
            if voice_enabled[cx16.r1L] {
                envelope_states[cx16.r1L] = 255
                volume(cx16.r1L, 0)
            }
        }
    }

    sub envelopes_irq() -> bool {
        ; If you want to use real-time volume envelopes (Attack-Sustain-Release),
        ; you have to call this routine every 1/60th second, for example from your vsync irq handler,
        ; or just install this routine as the only irq handler if you don't have to do other things there.
        ; Example: cx16.set_vsync_irq_handler(&psg.envelopes_irq)
        ; NOTE: this routine calls save/restore_vera_context() for you, don't nest this or call it yourself!

        ; cx16.r0 = the volume word (volume scaled by 256)
        ; cx16.r1L = the voice number
        ; cx16.r2L = attack value
        sys.pushw(cx16.r0)
        sys.push(cx16.r1L)
        sys.push(cx16.r2L)
        sys.pushw(cx16.r9)
        ; calculate new volumes
        for cx16.r1L in 0 to 15 {
            if not voice_enabled[cx16.r1L]
                continue
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
                    if envelope_sustains[cx16.r1L]!=0 {
                        envelope_sustains[cx16.r1L]--
                    } else {
                        envelope_states[cx16.r1L] = 2  ; start release
                    }
                }
                2 -> {
                    ; release
                    cx16.r0 = envelope_volumes[cx16.r1L] - envelope_releases[cx16.r1L] * $0040
                    if msb(cx16.r0) & %11000000 !=0 {
                        cx16.r0 = 0
                        envelope_releases[cx16.r1L] = 0
                    }
                    envelope_volumes[cx16.r1L] = cx16.r0
                }
            }
        }

        ; set new volumes of all 16 voices, using vera stride of 4
        cx16.save_vera_context()
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = $c2
        cx16.VERA_ADDR_M = $f9
        cx16.VERA_ADDR_H = 1 | %00110000
        cx16.VERA_CTRL = 1
        cx16.VERA_ADDR_L = $c2
        cx16.VERA_ADDR_M = $f9
        cx16.VERA_ADDR_H = 1 | %00110000
        for cx16.r1L in 0 to 15 {
            if voice_enabled[cx16.r1L]
                cx16.VERA_DATA0 = cx16.VERA_DATA1 & %11000000 | msb(envelope_volumes[cx16.r1L])
            else
                cx16.VERA_DATA0 = cx16.VERA_DATA1
        }
        cx16.restore_vera_context()
        cx16.r9 = sys.popw()
        cx16.r2L = sys.pop()
        cx16.r1L = sys.pop()
        cx16.r0 = sys.popw()
        return true     ; run the system IRQ handler afterwards
    }

    bool[16] voice_enabled
    ubyte[16] envelope_states
    uword[16] envelope_volumes      ; scaled by 256
    ubyte[16] envelope_attacks
    ubyte[16] envelope_sustains
    ubyte[16] envelope_releases
    ubyte[16] envelope_maxvolumes
}
