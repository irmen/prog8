; Experimental trackdisk.device streaming prototype (Amiga).
; Reads raw signed 8-bit MONO PCM from a dedicated RAW (non-DOS) floppy disk
; using simple double buffering and plays it via audio.device on both channels
; (dual mono: the same sample buffer is queued on channel 0 and channel 1).
;
; This relies on the design assumption that sequential track reads complete
; faster than the audio chunk plays. It is NOT robust against large seeks or
; retries; for a more forgiving (but more complex) ring-buffered version see
; earlier revisions of this file.
;
; Disk layout expected (logical-linear, like a raw .adf sector dump):
; PCM bytes start at START_OFFSET and run contiguously, one 5632-byte track
; per CMD_READ (11 sectors x 512). Pad the image to TOTAL_TRACKS*5632 bytes.
; This prototype does NOT parse FFS: for a normal DOS file use thriller.p8.
;
; Default UNIT=1 (DF1) so the boot/program disk in DF0 is untouched. Only set
; UNIT=0 if the program runs from RAM/HD and the raw audio disk is in DF0.
; Buffers live in chip RAM: required for trackdisk DMA on Kickstart < V36.

%import exec
%import trackdisk
%import audio
%import textio

main {
    const long UNIT = 1                 ; trackdisk unit: 0=DF0, 1=DF1
    const long START_OFFSET = 0         ; byte offset of PCM start on the raw disk
    const uword FRAMES_PER_TRACK = 5632 ; = trackdisk.TRACK_BYTES; mono: 1 byte = 1 frame
    const uword TOTAL_TRACKS = 80*2      ; full DD disk: 80 cylinders x 2 sides
    const uword SAMPLE_RATE = 14000
    const ubyte VOLUME = 64

    ^^byte[2] mono                      ; double buffer: [back, front] alternates

    sub start() {
        mono[0] = &sounddata.mono0
        mono[1] = &sounddata.mono1

        txt.print("trackdisk streaming prototype: raw disk unit ")
        txt.print_l(UNIT)
        txt.print(", tracks ")
        txt.print_uw(TOTAL_TRACKS)
        txt.print("\n")

        if not trackdisk.opendevice(UNIT) {
            txt.print("cannot open trackdisk.device unit\n")
            sys.exit(1)
        }
        defer trackdisk.closedevice()
        defer void trackdisk.motor(false)   ; LIFO: motor stops before the device closes

        if not audio.init() {
            txt.print("cannot open audio.device\n")
            sys.exit(1)
        }
        defer audio.closedown()

        ubyte back = 0
        uword chunk = 0
        long total_bytes = 0
        byte err

        ; prime: load and queue the first two chunks on both channels
        while chunk < 2 and chunk < TOTAL_TRACKS {
            err = trackdisk.read(mono[back], trackdisk.TRACK_BYTES,
                                      START_OFFSET + (chunk as long)*trackdisk.TRACK_BYTES)
            if err != 0 {
                txt.print("\ntrack read error\n")
                sys.exit(2)
            }
            txt.chrout('.')
            txt.flush()
            total_bytes += trackdisk.TRACK_BYTES
            audio.play_queued(0, mono[back], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME, 1)
            audio.play_queued(1, mono[back], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME, 1)
            back = 1 - back
            chunk++
        }

        ; steady state: reap the oldest queued chunk on both channels so the
        ; buffer is free, load the next track into it, and queue it on both.
        while chunk < TOTAL_TRACKS {
            audio.wait_queued(0)
            audio.wait_queued(1)
            err = trackdisk.read(mono[back], trackdisk.TRACK_BYTES,
                                      START_OFFSET + (chunk as long)*trackdisk.TRACK_BYTES)
            if err != 0 {
                txt.print("\ntrack read error\n")
                sys.exit(2)
            }
            txt.chrout('.')
            txt.flush()
            total_bytes += trackdisk.TRACK_BYTES
            audio.play_queued(0, mono[back], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME, 1)
            audio.play_queued(1, mono[back], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME, 1)
            back = 1 - back
            chunk++
        }

        txt.nl()
        audio.wait_all()
        txt.print("done, played ")
        txt.print_l(total_bytes)
        txt.print(" bytes\n")
    }
}

sounddata {
    %option amiga_chipram     ; trackdisk DMA target and audio DMA source
    byte[trackdisk.TRACK_BYTES] mono0
    byte[trackdisk.TRACK_BYTES] mono1
}
