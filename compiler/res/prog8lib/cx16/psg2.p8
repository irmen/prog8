; Vera PSG abstraction module, version 2
; Supersedes (and is not compatible to) the previous psg module which had some problems (timings were not consistent, etc)
; This new module requires setting up the voice parameters in their Voice structure,
; and then calling the update() routine periodically to apply the changes.
; You can read and write all parameters in the Voice structure as desired.
; For efficiency reasons, the volume envelope state is not kept in there though.
;
; Envelope timing (ASR - Attack, Sustain, Release):
;   The envelope advances by one tick each time update() is called.
;   The actual real-time duration depends on how often you call update().
;   If you call it every vsync (60Hz), 1 tick = ~16.7ms.
;   Attack and Release produce linear volume ramps.
;   Higher speed = faster ramp (shorter duration).
;   Timing formula:  duration_ticks = ceil(256 / speed)
;     speed=1   -> 256 ticks  (at 60Hz: ~4.3s)
;     speed=4   -> 64 ticks   (at 60Hz: ~1.07s)
;     speed=64  -> 4 ticks    (at 60Hz: ~67ms)
;     speed=255 -> 1 tick     (instant envelope change on next update() call)
;   Even speed=255 applies on the *next* call to ``update()`` — there is always at least ~1 frame of delay if you depend on the normal update cycle.
;   For truly immediate changes (0ms), set the voice struct fields and call ``update()`` directly.
;   Sustain holds for speed ticks (0-255) then transitions to release.
;   If speed=0 the envelope stalls at that phase (no progression - useful for infinite sustain).
;   The envelope volume is tracked internally as 8.8 fixed point.

; Vera PSG registers:
; $1F9C0 - $1F9FF 	16 blocks of 4 PSG registers (16 voices)
; 00  frequency word LSB
; 01  frequency word MSB.    freqword = HERZ / 0.3725290298461914
; 02  bit 7 =right, bit 6 = left, bits 5-0 = volume 0-63 levels
; 03  bit 7,6 = waveform, bits 5-0 = Pulse width 0-63
; waveform: 0=pulse, 1=sawtooth, 2=triangle, 3=noise


psg2 {
    %option ignore_unused

    ; waveform options:
    const ubyte PULSE    = %00000000
    const ubyte SQUARE   = %00000000        ; same as PULSE
    const ubyte SAWTOOTH = %01000000
    const ubyte TRIANGLE = %10000000
    const ubyte NOISE    = %11000000

    ; channels:
    const ubyte LEFT     = %01000000
    const ubyte RIGHT    = %10000000
    const ubyte BOTH     = %11000000
    const ubyte DISABLED = %00000000

    ; envelope states:
    enum EnvelopeState {
        OFF,
        ATTACK,
        SUSTAIN,
        RELEASE
    }

    struct Voice {
        ubyte channels          ; LEFT,RIGHT,BOTH or DISABLED
        ubyte volume            ; 0-63, use setvolume() to adjust this if you are also using ADSR envelope
        ubyte waveform          ; PULSE/SQUARE,SAWTOOTH,TRIANGLE,NOISE
        ubyte pulsewidth        ; 0-63
        uword frequency         ; direct VERA PSG register value (no scaling): freqword = HERZ(Hz) / 0.3725290298461914
    }


    ; envelope parameters are kept in arrays for maximum efficiency
    private ubyte[16] envelope_states
    private ubyte[16] envelope_attacks
    private ubyte[16] envelope_sustains
    private ubyte[16] envelope_releases
    private ubyte[16] envelope_maxvolumes
    private uword[16] envelope_volumes         ; 8.8 fixed point (scaled by 256)

    ; private VERA context save buffer, so we don't clobber the global _vera_storage in syslib
    ; (which is shared with the cx16 module's save_vera_context and would cause a race
    ;  condition if update() is interrupted by a VSYNC IRQ handler that also saves VERA context)
    private ubyte[8] @shared vera_storage

    ^^Voice voices = memory("psg2voices", 16*sizeof(Voice), 0)      ; can't use array of structs so use mem block
    private ^^Voice vptr

    sub init() {
        ; -- required before using this module; initializes all registers to default (off) values
        sys.irqsafe_set_irqd()
        sys.memset(voices, 16*sizeof(Voice), 0)
        sys.irqsafe_clear_irqd()
        for cx16.r0L in 0 to 15
            envelope_states[cx16.r0L] = EnvelopeState::OFF
    }

    sub off() {
        ; -- turn off all voices immediately
        sys.irqsafe_set_irqd()
        vptr = voices
        for cx16.r0L in 0 to 15 {
            vptr.channels = DISABLED
            vptr++
            envelope_states[cx16.r0L] = EnvelopeState::OFF
        }
        sys.irqsafe_clear_irqd()
        void update()
    }

    sub voice(ubyte voice_num, ubyte channels, ubyte volume, ubyte waveform, ubyte pulsewidth) {
        ; -- sets all parameters for a single voice (frequency is unchanged - set that separately)
        if voice_num > 15
            return
        volume &= %00111111
        envelope_states[voice_num] = EnvelopeState::OFF
        sys.irqsafe_set_irqd()
        vptr = &voices[voice_num]
        vptr.channels = channels
        vptr.volume = volume
        vptr.waveform = waveform
        vptr.pulsewidth = pulsewidth & %00111111
        ; do not change frequency, user is expected to call frequency() separately
        sys.irqsafe_clear_irqd()
        envelope_volumes[voice_num] = mkword(volume, 0)
        envelope_maxvolumes[voice_num] = volume
    }

    sub frequency(ubyte voice_num, uword @nozp freq) {
        ; -- sets new frequence for this voice
        vptr = &voices[voice_num]
        vptr.frequency = freq
    }

    sub volume(ubyte voice_num, ubyte @nozp vol) {
        ; -- Modifies the volume of this voice, adjusting the envelope as needed.
        ;    voice_num = 0-15, vol = 0-63 where 0=silent, 63=loudest.
        envelope_states[voice_num] = EnvelopeState::OFF
        vptr = &voices[voice_num]
        vptr.volume = vol
        envelope_volumes[voice_num] = mkword(vol, 0)
        envelope_maxvolumes[voice_num] = vol
    }

    sub envelope(ubyte voice_num, ubyte @nozp attack, ubyte @nozp sustain, ubyte @nozp release) {
        ; -- sets ASR envelope parameters for this voice
        envelope_states[voice_num] = EnvelopeState::OFF
        vptr = &voices[voice_num]
        vptr.volume = 0
        envelope_attacks[voice_num] = attack
        envelope_sustains[voice_num] = sustain
        envelope_releases[voice_num] = release
        envelope_volumes[voice_num] = 0
        envelope_states[voice_num] = EnvelopeState::ATTACK
    }

    sub getvoice(ubyte @nozp voice_num) -> ^^Voice {
        ; -- returns pointer to voice parameters
        return &voices[voice_num]
    }

    sub update() -> bool {
        ; -- update adsr envelopes, then write all 16 voices to Vera PSG. This call is IRQ-safe!
        ; This has to be called every time you want to apply changed psg voice parameters.
        ; If you want to use real-time volume envelopes (Attack-Sustain-Release),
        ; you have to call this routine every 1/60th second, for example from your vsync irq handler.
        ; Or just install this routine as the only irq handler if you don't have to do other things there.

        push(cx16.r0L)
        pushw(cx16.r1)
        pushw(cx16.r2)
        pushw(cx16.r3)

        vptr = voices
        alias voice = cx16.r0L
        alias maxvolume = cx16.r1
        alias newvolume = cx16.r2
        alias currentvolume = cx16.r3

        ; update active envelopes
        for voice in 0 to 15 {
            when envelope_states[voice] {
                EnvelopeState::ATTACK -> {
                    ; while current volume is less than max volume, increase volume by maxvolume * (attackspeed /256.0)
                    ; special case: speed=255 jumps directly to max volume (instant)
                    maxvolume = mkword(envelope_maxvolumes[voice], 0)
                    currentvolume = envelope_volumes[voice]
                    if currentvolume < maxvolume {
                        if envelope_attacks[voice] == 255
                            newvolume = maxvolume
                        else {
                            newvolume = currentvolume + (maxvolume >> 8)*envelope_attacks[voice]
                            newvolume = min(newvolume, maxvolume)
                        }
                        envelope_volumes[voice] = newvolume
                        vptr.volume = msb(newvolume)
                    } else
                        envelope_states[voice] = EnvelopeState::SUSTAIN
                }
                EnvelopeState::SUSTAIN -> {
                    if envelope_sustains[voice] > 0
                        envelope_sustains[voice]--
                    else
                        envelope_states[voice] = EnvelopeState::RELEASE
                }
                EnvelopeState::RELEASE -> {
                    ; while current volume is not zero, decrease volume by maxvolume * (releasespeed /256.0)
                    ; special case: speed=255 jumps directly to zero (instant)
                    currentvolume = envelope_volumes[voice]
                    if currentvolume > 0 {
                        if envelope_releases[voice] == 255
                            newvolume = 0
                        else {
                            uword subtraction = (envelope_maxvolumes[voice] as uword) * envelope_releases[voice]
                            if subtraction > currentvolume
                                newvolume = 0
                            else
                                newvolume = currentvolume - subtraction
                        }
                        envelope_volumes[voice] = newvolume
                        vptr.volume = msb(newvolume)
                    } else
                        envelope_states[voice] = EnvelopeState::OFF
                }
            }

            vptr++
        }

        psg2_save_vera_context()
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR = lsw(cx16.VERA_PSG_BASE)
        cx16.VERA_ADDR_H = %00010000 | msw(cx16.VERA_PSG_BASE)

        vptr = voices
        repeat 16 {
            if vptr.channels != DISABLED {
                cx16.VERA_DATA0 = lsb(vptr.frequency)
                cx16.VERA_DATA0 = msb(vptr.frequency)
                cx16.VERA_DATA0 = vptr.channels | (vptr.volume & %00111111)
                cx16.VERA_DATA0 = vptr.waveform | (vptr.pulsewidth & %00111111)
            } else {
                unroll 4 cx16.VERA_DATA0 = 0
            }
            vptr++
        }
        psg2_restore_vera_context()
        cx16.r3 = popw()
        cx16.r2 = popw()
        cx16.r1 = popw()
        cx16.r0L = pop()

        return true
    }

    private asmsub psg2_save_vera_context() clobbers(A) {
        %asm {{
            ; save VERA registers to our own private storage (not clobbering syslib's global _vera_storage)
            lda  cx16.VERA_ADDR_L
            sta  p8b_psg2.p8v_vera_storage
            lda  cx16.VERA_ADDR_M
            sta  p8b_psg2.p8v_vera_storage+1
            lda  cx16.VERA_ADDR_H
            sta  p8b_psg2.p8v_vera_storage+2
            lda  cx16.VERA_CTRL
            sta  p8b_psg2.p8v_vera_storage+3
            eor  #1
            sta  p8b_psg2.p8v_vera_storage+7
            sta  cx16.VERA_CTRL
            lda  cx16.VERA_ADDR_L
            sta  p8b_psg2.p8v_vera_storage+4
            lda  cx16.VERA_ADDR_M
            sta  p8b_psg2.p8v_vera_storage+5
            lda  cx16.VERA_ADDR_H
            sta  p8b_psg2.p8v_vera_storage+6
            rts
        }}
    }

    private asmsub psg2_restore_vera_context() clobbers(A) {
        %asm {{
            lda  p8b_psg2.p8v_vera_storage+7
            sta  cx16.VERA_CTRL
            lda  p8b_psg2.p8v_vera_storage+6
            sta  cx16.VERA_ADDR_H
            lda  p8b_psg2.p8v_vera_storage+5
            sta  cx16.VERA_ADDR_M
            lda  p8b_psg2.p8v_vera_storage+4
            sta  cx16.VERA_ADDR_L
            lda  p8b_psg2.p8v_vera_storage+3
            sta  cx16.VERA_CTRL
            lda  p8b_psg2.p8v_vera_storage+2
            sta  cx16.VERA_ADDR_H
            lda  p8b_psg2.p8v_vera_storage+1
            sta  cx16.VERA_ADDR_M
            lda  p8b_psg2.p8v_vera_storage+0
            sta  cx16.VERA_ADDR_L
            rts
        }}
    }
}
