; AHI streaming music player for ADPCM data on a raw (non-DOS) floppy disk.
; Reads compressed mono IMA ADPCM from a dedicated RAW disk with
; trackdisk.device, decodes it to 16-bit signed big-endian mono PCM, and plays
; it through ahi.device (AHIST_M16S), which does the mixing and output.
;
; Each 5632-byte track (11 sectors x 512) is read into a chip-RAM staging
; buffer and decoded into 11110 mono PCM frames (22220 bytes) straight into
; one of three PCM buffer sets, then queued on ahi.play_queued() with two
; requests in flight. Three sets are needed so the set being decoded is never
; one a request is still playing; ahi.play_queued() reaps the oldest chunk
; itself, so no explicit waiting is needed anywhere.
;
; This relies on the design assumption that sequential track reads plus
; decoding complete faster than the audio chunk plays. It is NOT robust against
; large seeks or retries; for a more forgiving (but more complex) ring-buffered
; version see earlier revisions of this file.
;
; Disk layout expected (logical-linear, like a raw .adf sector dump):
; ADPCM bytes start at START_OFFSET and run contiguously, one 5632-byte track
; per CMD_READ (11 sectors x 512) = 22 x 256-byte ADPCM blocks = 11110 PCM
; frames. Pad the image to TOTAL_TRACKS*5632 bytes. This prototype does NOT
; parse FFS: for a normal DOS file use streammusic.p8.
;
; The drive unit is selected interactively at startup. Keep the boot/program
; disk in the other drive when selecting the music data disk.
; No chip RAM is needed anywhere: AHI mixes from normal memory, and the
; trackdisk CMD_READ chip memory restriction only exists on Kickstart < V36,
; while this program targets Kickstart 3+ (V36+). AHI playback uses the mode
; configured for AHI unit 0 in ENV:Sys/ahi.prefs and resamples the 16000 Hz
; input to the mode's mix rate.

%import exec
%import dos
%import trackdisk
%import ahi
%import adpcm
%import textio

main {
    const long START_OFFSET = 0         ; byte offset of ADPCM start on the raw disk
    const uword COMPRESSED_TRACK_BYTES = 5632
    const uword ADPCM_BLOCK_BYTES = 256
    const ubyte BLOCKS_PER_TRACK = 22
    const uword SAMPLES_PER_BLOCK = 505
    const uword FRAMES_PER_TRACK = BLOCKS_PER_TRACK * SAMPLES_PER_BLOCK   ; 11110
    const uword PCM_TRACK_BYTES = FRAMES_PER_TRACK*2                     ; 22220
    const uword TOTAL_TRACKS = 80*2      ; full DD disk: 80 cylinders x 2 sides
    const uword SAMPLE_RATE = 16000
    const long DISK_DATA_RATE = COMPRESSED_TRACK_BYTES * SAMPLE_RATE / FRAMES_PER_TRACK
    const long TOTAL_DATA_BYTES = COMPRESSED_TRACK_BYTES * TOTAL_TRACKS
    const long TOTAL_FRAMES = FRAMES_PER_TRACK * TOTAL_TRACKS
    const long TYPE = ahi.AHIST_M16S

    ^^byte[3] pcm                       ; decoded mono 16-bit PCM, per buffer set
    pointer staging                     ; compressed ADPCM track read target

    sub start() {
        pcm[0] = &buffers.pcm0
        pcm[1] = &buffers.pcm1
        pcm[2] = &buffers.pcm2
        staging = &buffers.staging

        ubyte unit = blurb()

        if not trackdisk.opendevice(unit) {
            txt.print("cannot open trackdisk.device unit\n")
            sys.exit(1)
        }
        defer trackdisk.closedevice()
        defer void trackdisk.motor(false)   ; LIFO: motor stops before the device closes

        if not ahi.open(ahi.AHI_DEFAULT_UNIT) {
            txt.print("cannot open ahi.device\n")
            sys.exit(1)
        }
        defer ahi.close()

        ubyte set = 0
        uword chunk = 0
        long total_bytes = 0
        byte err
        bool stopped = false

        ; read, decode and queue every track. ahi.play_queued() keeps two
        ; requests in flight and reaps the oldest one when both slots are
        ; taken, so no explicit waiting is needed anywhere in this loop.
        while chunk < TOTAL_TRACKS {
            if ctrl_c_pressed() {
                stopped = true
                break
            }
            err = trackdisk.read(staging, COMPRESSED_TRACK_BYTES,
                                      START_OFFSET + (chunk as long)*COMPRESSED_TRACK_BYTES)
            if err != 0 {
                txt.print("\ntrack read error\n")
                ahi.stop()
                stopped = true
                break
            }
            txt.chrout('.')
            txt.flush()
            total_bytes += COMPRESSED_TRACK_BYTES
            decode_track(staging, pcm[set])
            ahi.play_queued(pcm[set], PCM_TRACK_BYTES as long, TYPE, SAMPLE_RATE, ahi.FULL_VOLUME, ahi.PAN_CENTER)
            set = (set+1) % 3
            chunk++
        }

        if stopped {
            txt.print("\nStopped, played ")
            txt.print_l(total_bytes)
            txt.print(" bytes\n")
        } else {
            txt.nl()
            ahi.wait()
            txt.print("done, played ")
            txt.print_l(total_bytes)
            txt.print(" bytes\n")
        }
    }

    sub ctrl_c_pressed() -> bool {
        return (exec.SetSignal(0, dos.SIGBREAKF_CTRL_C) & dos.SIGBREAKF_CTRL_C) != 0
    }

    ; Decode one compressed track (22 x 256-byte ADPCM blocks) into contiguous
    ; mono 16-bit big-endian PCM. Each block produces 1010 bytes, written
    ; straight into its place in the PCM buffer, so no scratch space is needed.
    sub decode_track(pointer input, pointer pcm_out) {
        ubyte block = 0
        pointer src = input
        pointer dst = pcm_out
        while block < BLOCKS_PER_TRACK {
            adpcm.decode_block_mono(src, dst)
            src += ADPCM_BLOCK_BYTES
            dst += SAMPLES_PER_BLOCK*2
            block++
        }
    }

    sub blurb() -> ubyte {
        txt.print("\n\nADPCM music streamer, requires 68020+ for smooth playback.\n")
        txt.print("Plays on ahi.device on unit 0, as configured in AHIPrefs\n")
        txt.print("Sample rate: ")
        txt.print_uw(SAMPLE_RATE)
        txt.print(" Hz\nDisk data rate: ")
        txt.print_l(DISK_DATA_RATE)
        txt.print(" bytes/sec\n")
        txt.print("Raw data size: ")
        txt.print_l(TOTAL_DATA_BYTES)
        txt.print(" bytes\nPlayback duration: ")
        txt.print_l((TOTAL_FRAMES + SAMPLE_RATE - 1) / SAMPLE_RATE)
        txt.print(" sec.\n\n")

        str drive_input = "?" * 4
        txt.print("The music is on a raw data disk that the OS cannot recognise\n")
        txt.print("Insert it in DF0 - DF3, then enter the drive nr 0-3: ")
        void txt.input_chars(drive_input)
        if drive_input[0] <'0' or drive_input[1]>'3' {
            txt.print("invalid drive number\n")
            sys.exit(1)
        }
        return drive_input[0] - '0'
    }
}

buffers {
    ; trackdisk CMD_READ staging (one compressed track) and the decoded mono
    ; 16-bit big-endian PCM, one track per set
    byte[main.COMPRESSED_TRACK_BYTES] staging
    byte[main.PCM_TRACK_BYTES] pcm0
    byte[main.PCM_TRACK_BYTES] pcm1
    byte[main.PCM_TRACK_BYTES] pcm2
}
