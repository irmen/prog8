%import ahi
%import dos
%import exec
%import custom
%import textio
%import wavfile

; Prog8 port of the AHI developer-kit example Device/PlayTest: streams a sample
; file to ahi.device through the high-level CMD_WRITE interface,
; gapless via ahir_Link request chaining.
;
; Requires Kickstart 3+ (compile with -target amiga1200).
;
; The sample file is given as the first command line argument, containing
; 16-bit mono or stereo PCM WAV data. Sample rate and channel layout come
; from the WAV header itself (parsed with the wavfile library); AHI resamples
; to the output mode's mix rate. WAV samples are little-endian and are
; byte-swapped to big-endian before queueing.
;
; Usage: ahiplay mysample.wav

main {
    const ubyte UNIT = ahi.AHI_DEFAULT_UNIT
    ; Must be a whole number of sample frames, which are 4 bytes each for
    ; stereo (two 2-byte channels) or 2 bytes for mono. 32768 bytes is 8192
    ; stereo frames, about 0.37 seconds of audio at 22050 Hz.
    const uword BUFFERSIZE = 32768
    const uword HEADER_BYTES = 1024   ; enough to hold the fmt chunk and the data chunk header

    ; ahi.play_queued() can have two requests in flight at once, so three
    ; buffers are needed: one being played, one queued, and one free to refill
    ; from the file. With only two, the refill would overwrite a playing buffer.
    ubyte[BUFFERSIZE] buffer1
    ubyte[BUFFERSIZE] buffer2
    ubyte[BUFFERSIZE] buffer3
    pointer[3] buffers = [&buffer1, &buffer2, &buffer3]

    sub start() {
        play_sample(dos.GetArgStr())
    }

    sub play_sample(str samplefile) {
        ; -- Play the named wav file. The file argument comes from the
        ;    command line (null when the program was started without one).
        if samplefile==0 or ""==samplefile {
            txt.print("usage: ahiplay <sample.wav>\n")
            return
        }
        txt.print("input: ")
        txt.print(samplefile)
        txt.print("\n")

        pointer infile = dos.Open(samplefile, dos.MODE_OLDFILE)
        if infile==0 {
            txt.print("cannot open the sample file\n")
            return
        }
        defer void dos.Close(infile)

        ; read the wav header and locate the sample data (skips LIST and other extra chunks)
        long n = dos.Read(infile, buffers[0], HEADER_BYTES as long)
        if n!=(HEADER_BYTES as long) or not wavfile.parse_header(buffers[0]) {
            txt.print("not a valid wav file\n")
            return
        }
        if wavfile.wavefmt!=wavfile.Format::PCM or wavfile.bits_per_sample!=16 or wavfile.nchannels<1 or wavfile.nchannels>2 {
            txt.print("need 16-bit mono/stereo PCM\n")
            return
        }
        void dos.Seek(infile, wavfile.data_offset, dos.OFFSET_BEGINNING)

        long type = ahi.AHIST_S16S
        if wavfile.nchannels==1 {
            type = ahi.AHIST_M16S
        }

        if not ahi.open(UNIT) {
            txt.print("cannot open ahi.device\n")
            return
        }
        defer ahi.close()
        custom.filter_off()     ; disable the muffled lowpass filter for crisp audio

        txt.print("ahiplay: 16-bit ")
        if wavfile.nchannels==2 {
            txt.print("stereo @ ")
        } else {
            txt.print("mono @ ")
        }
        txt.print_uw(wavfile.sample_rate)
        txt.print(" Hz\n")
        txt.print("playing on unit ")
        txt.print_ub(UNIT)
        txt.print(" (output mode as configured in ENV:Sys/ahi.prefs)\n")

        ubyte bufidx = 0
        long length
        long remaining = wavfile.data_size
        repeat {
            if (exec.SetSignal(0, dos.SIGBREAKF_CTRL_C) & dos.SIGBREAKF_CTRL_C)!=0 {
                txt.print("\ninterrupted\n")
                ahi.stop()
                break
            }
            long want = BUFFERSIZE as long
            if want>remaining
                want = remaining
            length = dos.Read(infile, buffers[bufidx], want)
            if length<0 {
                txt.print("\nread error\n")
                ahi.stop()
                break
            }
            if length==0
                break
            remaining -= length
            ; WAV is little-endian: swap every word to big-endian for AHI
            long words = length/2
            wavfile.convert_16bit_mono(buffers[bufidx], words)
            long playlen = words*2
            if playlen>=2 {
                ahi.play_queued(buffers[bufidx], playlen, type, wavfile.sample_rate, ahi.FULL_VOLUME, ahi.PAN_CENTER)
                ; one dot per buffer as a progress indicator
                txt.chrout('.')
                txt.flush()
                bufidx++
                if bufidx>=3
                    bufidx = 0
            }
            if remaining<=0
                break
        }

        ; let whatever is still queued finish playing (a no-op after stop())
        ahi.wait()
        txt.print("\n")
        txt.print("done\n")
    }
}
