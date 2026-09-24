%import ahi
%import dos
%import exec
%import textio

; Prog8 port of the AHI developer-kit example Device/PlayTest: streams a raw
; sample file to ahi.device through the high-level CMD_WRITE interface,
; gapless via ahir_Link request chaining.
;
; Requires Kickstart 3+ (compile with -target amiga1200).
;
; The sample file is given as the first command line argument, containing
; stereo (interleaved left, right), 16-bit signed big-endian PCM
; (AHI AHIST_S16S) at 22050 Hz. The m68k is big-endian, so raw AIFF sample
; data can be used as-is; RIFF/WAV data is little-endian and has to be
; byte-swapped first.
;
; Usage: ahiplay mysample.raw

main {
    const ubyte UNIT = ahi.AHI_DEFAULT_UNIT
    const long TYPE = ahi.AHIST_S16S
    const uword FREQUENCY = 22050
    ; Must be a whole number of sample frames, which are 4 bytes each
    ; (two 2-byte channels). 32768 bytes is 8192 frames, about 0.37 seconds
    ; of audio.
    const uword BUFFERSIZE = 32768

    ; ahi.play_queued() can have two requests in flight at once, so three
    ; buffers are needed: one being played, one queued, and one free to refill
    ; from the file. With only two, the refill would overwrite a playing buffer.
    ubyte[BUFFERSIZE] buffer1
    ubyte[BUFFERSIZE] buffer2
    ubyte[BUFFERSIZE] buffer3
    pointer[3] buffers = [&buffer1, &buffer2, &buffer3]

    sub start() {
        txt.print("ahiplay: 16-bit signed big-endian stereo @ ")
        txt.print_uw(FREQUENCY)
        txt.print(" Hz\n")
        play_sample(dos.GetArgStr())
    }

    sub play_sample(str samplefile) {
        ; -- Play the named raw sample file. The file argument comes from the
        ;    command line (null when the program was started without one).
        if samplefile==0 {
            txt.print("usage: ahiplay <sample.raw>\n")
            return
        }
        txt.print("input: ")
        txt.print(samplefile)
        txt.print("\n")
        if not ahi.open(UNIT) {
            txt.print("cannot open ahi.device\n")
            return
        }
        txt.print("playing on unit ")
        txt.print_ub(UNIT)
        txt.print(" (output mode as configured in ENV:Sys/ahi.prefs)\n")

        pointer infile = dos.Open(samplefile, dos.MODE_OLDFILE)
        if infile==0 {
            txt.print("cannot open the sample file\n")
            ahi.close()
            return
        }

        ubyte bufidx = 0
        long length
        repeat {
            if (exec.SetSignal(0, dos.SIGBREAKF_CTRL_C) & dos.SIGBREAKF_CTRL_C)!=0 {
                txt.print("\ninterrupted\n")
                ahi.stop()
                break
            }
            length = dos.Read(infile, buffers[bufidx], BUFFERSIZE)
            if length<0 {
                txt.print("\nread error\n")
                ahi.stop()
                break
            }
            if length<BUFFERSIZE {
                ; a short read means this is the last chunk of the sample
                if length>0
                    ahi.play_queued(buffers[bufidx], length, TYPE, FREQUENCY, ahi.FULL_VOLUME, ahi.PAN_CENTER)
                break
            }
            ahi.play_queued(buffers[bufidx], length, TYPE, FREQUENCY, ahi.FULL_VOLUME, ahi.PAN_CENTER)
            ; one dot per buffer as a progress indicator
            txt.chrout('.')
            txt.flush()
            bufidx++
            if bufidx>=3
                bufidx = 0
        }

        ; let whatever is still queued finish playing (a no-op after stop())
        ahi.wait()
        txt.print("\n")
        txt.print("done\n")
        void dos.Close(infile)
        ahi.close()
    }
}
