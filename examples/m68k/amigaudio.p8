%import audio
%import exec
%import textio


; TODO fix bug?  there is no sound playback, only silence...


main {
    const uword SAMPLE_RATE = 8000  ; Hz
    const uword SAMPLE_COUNT = 8000 ; 1 second buffer at 8000 Hz

    ; Channel allocation preference: try Ch 0, Ch 1, Ch 2, Ch 3 in order
    ubyte[4] channel_matrix = [1, 2, 4, 8]

    sub start() {
        txt.cls()
        txt.print("Audio test\n")

        ; 1. Allocate 8000 bytes in CHIP RAM
        pointer samplebuf = exec.AllocMem(SAMPLE_COUNT, exec.MEMF_CHIP | exec.MEMF_CLEAR)
        if samplebuf == 0 {
            txt.print("Failed to allocate CHIP RAM\n")
            return
        }

        ; 2. Generate a 8000-byte square wave directly in CHIP RAM
        ; (100 Hz tone: 40 bytes high, 40 bytes low repeating)
        uword i = 0
        repeat {
            if (i % 80) < 40 {
                samplebuf[i] = $40  ; +64
            } else {
                samplebuf[i] = $C0  ; -64
            }
            i++
            if i >= SAMPLE_COUNT break
        }

        ; Period = 3546895 / 8000 = 443
        uword period = 3546895 / SAMPLE_RATE

        ; 3. Open device passing array directly (no &&)
        txt.print("Opening audio device...\n")
        if audio.opendevice(channel_matrix, 4, 0) {
            txt.print("Device opened!\n")

            ; 3. Configure playback (explicit type casts for 68k alignment)
            audio.AudioIO.Command = exec.CMD_WRITE
            audio.AudioIO.Flags = audio.ADIOF_PERVOL          ; ubyte $10
            audio.AudioIO.Data = samplebuf
            audio.AudioIO.IOAudio_Length = SAMPLE_COUNT as long ; Force 32-bit store
            audio.AudioIO.Period = period                      ; uword 443
            audio.AudioIO.Volume = 64                          ; Max volume
            audio.AudioIO.Cycles = 3                           ; Play 3 seconds

            txt.print("Playing tone for 3 seconds...\n")

            exec.SendIO(audio.AudioIO)
            void exec.WaitIO(audio.AudioIO)

            txt.print("Error code: ")
            txt.print_b(audio.AudioIO.Error)
            txt.print("\n")

            audio.closedevice()
        } else {
            txt.print("Failed to open audio.device\n")
        }

        exec.FreeMem(samplebuf, SAMPLE_COUNT)
        txt.print("Done\n")
    }
}
