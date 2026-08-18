%import audio
%import exec
%import textio


main {
    const uword SAMPLE_RATE = 8000  ; Hz
    const uword SAMPLE_COUNT = 8000 ; 1 second buffer at 8000 Hz

    sub start() {
        txt.print("Audio test\n")

        ; 1. Allocate 8000 bytes in CHIP RAM
        pointer samplebuf = exec.AllocMem(SAMPLE_COUNT, exec.MEMF_CHIP | exec.MEMF_CLEAR)
        if samplebuf == 0 {
            txt.print("Failed to allocate CHIP RAM\n")
            return
        }

        ; 2. Generate a 1,000 Hz square wave directly in CHIP RAM
        ; (8 samples per cycle at 8000 Hz sample rate)
        uword i = 0
        repeat {
            if (i % 8) < 4 {
                samplebuf[i] = $40  ; +64
            } else {
                samplebuf[i] = $C0  ; -64
            }
            i++
            if i >= SAMPLE_COUNT break
        }

        ; 3. Open device passing array directly (no &&)
        txt.print("Opening audio device...\n")

        ; Bitmask 15 (1|2|4|8) forces allocation of ALL 4 CHANNELS
        ubyte[1] channel_matrix = [15]
        if audio.opendevice(channel_matrix, 1, 0) {

            ; 3. Configure playback (explicit type casts for 68k alignment)
            audio.AudioIO.Command = exec.CMD_WRITE
            audio.AudioIO.Flags = audio.ADIOF_PERVOL          ; ubyte $10
            audio.AudioIO.Data = samplebuf
            audio.AudioIO.IOAudio_Length = SAMPLE_COUNT as long ; Force 32-bit store
            audio.AudioIO.Period = audio.period(SAMPLE_RATE)
            audio.AudioIO.Volume = 64                          ; Max volume
            audio.AudioIO.Cycles = 3                           ; Play 3 seconds

            txt.print("Playing tone...\n")

            ; not exec.DoIO/SendIO: those clear io_Flags, wiping ADIOF_PERVOL
            audio.BeginIO(audio.AudioIO)
            void exec.WaitIO(audio.AudioIO)

            audio.closedevice()
        } else {
            txt.print("Failed to open audio.device\n")
        }

        exec.FreeMem(samplebuf, SAMPLE_COUNT)
        txt.print("Done\n")
    }
}
