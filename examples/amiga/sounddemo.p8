; Small Paula stereo demo. A sine wave and a noise wave are synthesized at
; runtime into CHIP RAM, so the program is self-contained (no sample files).
; Channels 0+3 are the left speaker, 1+2 the right: the sine loops on the
; left, the noise on the right. Over time the sine pitch sweeps up and the
; noise volume fades, then a short one-shot is played and waited for.
; Runs on amiga500 and amiga1200; compile with  prog8c -target amiga500 sounddemo.p8

%import custom
%import sound
%import textio
%import math

main {
    const uword SINE_SAMPLES = 128
    const uword SINE_CYCLES = 5
    const ubyte SINE_VOLUME = 40
    const uword TONE_MIN_HZ = 50
    const uword TONE_MAX_HZ = 1000
    const uword SINE_LOOP_WORDS = SINE_SAMPLES / 2
    const uword SINE_ONESHOT_WORDS = SINE_LOOP_WORDS + 1
    const uword NOISE_SAMPLES = 8192     ; avoid an audible short-loop pitch
    const uword NOISE_LEN_WORDS = NOISE_SAMPLES / 2

    sub start() {
        txt.print("Makeing waveforms...\n")
        make_waveforms()

        ; NOTE: this is a hardware banging program so it really should do custom.grab_system(),
        ;       but that prevents easy text output. We play dirty here and just bang Paula with OS enabled.

        txt.print("Example 1: L=sine wave rising freq, R=noise fade volume down.\n")

        sound.init()
        custom.filter_off()     ; disable the muffled lowpass filter for crisp audio
        const uword start_rate = TONE_MIN_HZ*SINE_SAMPLES/SINE_CYCLES
        const uword start_period = (sound.CLOCK_PAL/start_rate) as uword
        pointer sine_loop = &waveforms.sine + 2

        ; stereo: sine on the left pair (C0+C3), noise on the right pair (C1+C2)
        sound.loop(sound.Channel::C0, sine_loop, SINE_LOOP_WORDS, start_period, SINE_VOLUME)
        sound.loop(sound.Channel::C3, sine_loop, SINE_LOOP_WORDS, start_period, SINE_VOLUME)
        sound.loop(sound.Channel::C1, &waveforms.noise, NOISE_LEN_WORDS, 320, 60)
        sound.loop(sound.Channel::C2, &waveforms.noise, NOISE_LEN_WORDS, 320, 60)

        ; sweep the sine pitch 50->1000 Hz and fade the noise volume down.
        ; 191 steps of 5 Hz at 50 VBlanks/sec is about 3.8 seconds.
        for tone in TONE_MIN_HZ to TONE_MAX_HZ step 5 {
            custom.waitvsync()
            uword rate = (((tone as long)*SINE_SAMPLES/SINE_CYCLES) as uword)
            uword period = (sound.CLOCK_PAL/rate) as uword
            sound.set_period(sound.Channel::C0, period)
            sound.set_period(sound.Channel::C3, period)
            ubyte volume = (60 - (tone-TONE_MIN_HZ)*50/(TONE_MAX_HZ-TONE_MIN_HZ)) as ubyte
            sound.set_volume(sound.Channel::C1, volume)
            sound.set_volume(sound.Channel::C2, volume)
        }

        txt.print("Stop all sound.\n")
        sound.stop_all()
        sys.wait(30)

        txt.print("3x noise oneshot...\n")
        ; 3 one-shot noise bursts at different pitches (periods)
        sound.play(sound.Channel::C1, &waveforms.noise, NOISE_LEN_WORDS, 200, 64)
        sound.wait_channel(sound.Channel::C1)
        sys.wait(20)
        sound.play(sound.Channel::C1, &waveforms.noise, NOISE_LEN_WORDS, 350, 64)
        sound.wait_channel(sound.Channel::C1)
        sys.wait(20)
        sound.play(sound.Channel::C1, &waveforms.noise, NOISE_LEN_WORDS, 600, 64)

        txt.print("Wait till all sounds finish...\n")
        sound.wait_all()
        sound.closedown()

        txt.print("End.\n")
    }

    sub make_waveforms() {
        ; the first word is the silent loop point used by one-shot playback
        waveforms.sine[0] = 0
        waveforms.sine[1] = 0
        waveforms.noise[0] = 0
        waveforms.noise[1] = 0
        for i in 0 to SINE_SAMPLES-1 {
            ; 8-bit signed PCM: five full sine periods across the loop buffer
            waveforms.sine[i+2] = math.sin8((i*(256*SINE_CYCLES/SINE_SAMPLES)) as ubyte) as ubyte
        }
        for i in 2 to NOISE_SAMPLES-1 {
            waveforms.noise[i] = math.rnd()
        }
    }
}

waveforms {
    %option amiga_chipram

    ; one word = two consecutive Paula samples; filled at startup
    ubyte[130] sine
    ubyte[8192] noise
}
