; Experimental trackdisk.device streaming prototype (Amiga).
; Reads raw signed 16-bit MONO PCM (little-endian) from a dedicated RAW
; (non-DOS) floppy disk and plays it as 14-bit audio on all four channels.
; Paula's speakers: AUD0+AUD3 = left, AUD1+AUD2 = right, so each side gets one
; coarse and one fine channel (dual mono: both sides play the same signal).
; On each side the coarse channel (vol 64) plays the upper 8 bits of each
; 16-bit sample and the fine channel (vol 1) plays the next 6 most significant
; bits ((sample & $ff) >> 2), which together keep the top 14 bits per side.
;
; Each 5632-byte track is read into a shared chip-RAM staging buffer and
; split into a 2816-byte coarse and 2816-byte fine buffer (3 buffer sets, so a
; set being filled/decoded is never one the audio DMA engine is still reading).
;
; All four channels are started phase-locked: CMD_STOP queues our writes,
; then one CMD_START with bitmask 0x0F begins them at the same tick.
; Identical periods and lengths keep the coarse/fine pairs in sync from there.
;
; This relies on the design assumption that sequential track reads plus
; decoding complete faster than the audio chunk plays. It is NOT robust against
; large seeks or retries; for a more forgiving (but more complex) ring-buffered
; version see earlier revisions of this file.
;
; Disk layout expected (logical-linear, like a raw .adf sector dump):
; PCM bytes start at START_OFFSET and run contiguously, one 5632-byte track
; per CMD_READ (11 sectors x 512) = 2816 16-bit samples. Pad the image to
; TOTAL_TRACKS*5632 bytes. This prototype does NOT parse FFS: for a normal DOS
; file use streammusic.p8.
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
    const uword FRAMES_PER_TRACK = trackdisk.TRACK_BYTES/2 ; 2 bytes per 16-bit sample
    const uword TOTAL_TRACKS = 80*2      ; full DD disk: 80 cylinders x 2 sides
    const uword SAMPLE_RATE = 10000
    const ubyte VOLUME_COARSE = 64
    const ubyte VOLUME_FINE = 1

    ^^byte[3] coarse                    ; upper 8 bits of each sample, per buffer set
    ^^byte[3] fine                      ; ((sample & $ff) >> 2), per buffer set
    pointer staging                     ; raw 16-bit track read target

    sub start() {
        coarse[0] = &sounddata.coarse0
        coarse[1] = &sounddata.coarse1
        coarse[2] = &sounddata.coarse2
        fine[0] = &sounddata.fine0
        fine[1] = &sounddata.fine1
        fine[2] = &sounddata.fine2
        staging = &sounddata.staging

        txt.print("trackdisk 14-bit streaming prototype: raw disk unit ")
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

        ubyte set = 0
        uword chunk = 0
        long total_bytes = 0
        byte err

        ; stop all channels so the primed writes wait for the CMD_START below
        audio.stop_channels($0f)

        ; prime: load and queue the first two chunks on all four channels
        while chunk < 2 and chunk < TOTAL_TRACKS {
            err = trackdisk.read(staging, trackdisk.TRACK_BYTES,
                                      START_OFFSET + (chunk as long)*trackdisk.TRACK_BYTES)
            if err != 0 {
                txt.print("\ntrack read error\n")
                sys.exit(2)
            }
            txt.chrout('.')
            txt.flush()
            total_bytes += trackdisk.TRACK_BYTES
            split14(staging, coarse[set], fine[set], FRAMES_PER_TRACK)
            queue_chunk(set)
            set = (set+1) % 3
            chunk++
        }

        ; now start all four channels at the same tick, keeping the primed
        ; coarse/fine writes phase-locked on each speaker
        audio.start_channels($0f)

        ; steady state: reap the oldest queued chunk on all channels so its
        ; buffer set is free again, read the next track into the set that was
        ; queued two chunks ago, split it, and queue it on all channels.
        while chunk < TOTAL_TRACKS {
            audio.wait_queued(0)
            audio.wait_queued(1)
            audio.wait_queued(2)
            audio.wait_queued(3)
            err = trackdisk.read(staging, trackdisk.TRACK_BYTES,
                                      START_OFFSET + (chunk as long)*trackdisk.TRACK_BYTES)
            if err != 0 {
                txt.print("\ntrack read error\n")
                sys.exit(2)
            }
            txt.chrout('.')
            txt.flush()
            total_bytes += trackdisk.TRACK_BYTES
            split14(staging, coarse[set], fine[set], FRAMES_PER_TRACK)
            queue_chunk(set)
            set = (set+1) % 3
            chunk++
        }

        txt.nl()
        audio.wait_all()
        txt.print("done, played ")
        txt.print_l(total_bytes)
        txt.print(" bytes\n")
    }

    sub queue_chunk(ubyte set) {
        ; queue this chunk on all 4 channels: coarse at vol 64 on channels
        ; 0+1 (left+right), fine at vol 1 on channels 3+2 (left+right).
        audio.play_queued(0, coarse[set], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME_COARSE, 1)
        audio.play_queued(3, fine[set], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME_FINE, 1)
        audio.play_queued(1, coarse[set], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME_COARSE, 1)
        audio.play_queued(2, fine[set], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME_FINE, 1)
    }

    ; split 16-bit signed little-endian mono samples into a coarse (upper 8
    ; bits) and a fine ((sample & $ff) >> 2) stream, in separate buffers.
    asmsub split14(pointer raw @A0, ^^byte coarse @A1, ^^byte fine @A2, uword samples @D0) clobbers(D0,D1,D2,A0,A1,A2) {
        %asm {{
            tst.w   d0
            beq.s   .done
            subq.w  #1,d0
.split
            move.b  (a0)+,d1        ; low byte of LE sample
            move.b  (a0)+,d2        ; high byte of sample
            move.b  d2,(a1)+        ; coarse = top 8 bits
            lsr.b   #2,d1           ; fine = bits 2..7 of low byte
            move.b  d1,(a2)+
            dbra    d0,.split
.done
            rts
        }}
    }
}

sounddata {
    %option amiga_chipram     ; trackdisk DMA target and audio DMA target
    byte[trackdisk.TRACK_BYTES] staging
    byte[trackdisk.TRACK_BYTES/2] coarse0
    byte[trackdisk.TRACK_BYTES/2] fine0
    byte[trackdisk.TRACK_BYTES/2] coarse1
    byte[trackdisk.TRACK_BYTES/2] fine1
    byte[trackdisk.TRACK_BYTES/2] coarse2
    byte[trackdisk.TRACK_BYTES/2] fine2
}
