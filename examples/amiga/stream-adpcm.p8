; Experimental trackdisk.device streaming prototype (Amiga).
; Reads compressed mono IMA ADPCM from a dedicated RAW (non-DOS) floppy disk
; and plays it as 14-bit audio on all four channels.
; Paula's speakers: AUD0+AUD3 = left, AUD1+AUD2 = right, so each side gets one
; coarse and one fine channel (dual mono: both sides play the same signal).
; On each side the coarse channel (vol 64) plays the upper 8 bits of each
; 16-bit sample and the fine channel (vol 1) plays the next 6 most significant
; bits ((sample & $ff) >> 2), which together keep the top 14 bits per side.
;
; Each 5632-byte track is read into a shared chip-RAM staging buffer and
; decoded into 11110 signed 16-bit big-endian PCM samples, then split into a
; 11110-byte coarse and 11110-byte fine buffer (3 buffer sets, so a set being
; filled/decoded is never one the audio DMA engine is still reading).
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
; ADPCM bytes start at START_OFFSET and run contiguously, one 5632-byte track
; per CMD_READ (11 sectors x 512) = 22 x 256-byte ADPCM blocks = 11110 PCM
; frames. Pad the image to TOTAL_TRACKS*5632 bytes. This prototype does NOT
; parse FFS: for a normal DOS file use streammusic.p8.
;
; The drive unit is selected interactively at startup. Keep the boot/program
; disk in the other drive when selecting the music data disk.
; Buffers live in chip RAM: required for trackdisk DMA on Kickstart < V36.

%import exec
%import dos
%import trackdisk
%import audio
%import adpcm
%import textio

main {
    const long START_OFFSET = 0         ; byte offset of ADPCM start on the raw disk
    const uword COMPRESSED_TRACK_BYTES = 5632
    const uword ADPCM_BLOCK_BYTES = 256
    const ubyte BLOCKS_PER_TRACK = 22
    const uword SAMPLES_PER_BLOCK = 505
    const uword FRAMES_PER_TRACK = BLOCKS_PER_TRACK * SAMPLES_PER_BLOCK   ; 11110
    const uword TOTAL_TRACKS = 80*2      ; full DD disk: 80 cylinders x 2 sides
    const uword SAMPLE_RATE = 16000
    const long DISK_DATA_RATE = COMPRESSED_TRACK_BYTES * SAMPLE_RATE / FRAMES_PER_TRACK
    const long TOTAL_DATA_BYTES = COMPRESSED_TRACK_BYTES * TOTAL_TRACKS
    const long TOTAL_FRAMES = FRAMES_PER_TRACK * TOTAL_TRACKS
    const ubyte VOLUME_COARSE = 64
    const ubyte VOLUME_FINE = 1

    ^^byte[3] coarse                    ; upper 8 bits of each sample, per buffer set
    ^^byte[3] fine                      ; ((sample & $ff) >> 2), per buffer set
    pointer staging                     ; compressed ADPCM track read target
    pointer scratch                     ; decoded PCM scratch buffer for one ADPCM block

    sub start() {
        coarse[0] = &sounddata.coarse0
        coarse[1] = &sounddata.coarse1
        coarse[2] = &sounddata.coarse2
        fine[0] = &sounddata.fine0
        fine[1] = &sounddata.fine1
        fine[2] = &sounddata.fine2
        staging = &sounddata.staging
        scratch = &sounddata.scratch

        ubyte unit = blurb()

        if not trackdisk.opendevice(unit) {
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
        bool stopped = false

        ; stop all channels so the primed writes wait for the CMD_START below
        audio.stop_channels($0f)

        ; prime: load and queue the first two chunks on all four channels
        while chunk < 2 and chunk < TOTAL_TRACKS {
            if ctrl_c_pressed() {
                stopped = true
                break
            }
            err = trackdisk.read(staging, COMPRESSED_TRACK_BYTES,
                                      START_OFFSET + (chunk as long)*COMPRESSED_TRACK_BYTES)
            if err != 0 {
                txt.print("\ntrack read error\n")
                sys.exit(2)
            }
            txt.chrout('.')
            txt.flush()
            total_bytes += COMPRESSED_TRACK_BYTES
            decode_split_track(staging, coarse[set], fine[set])
            queue_chunk(set)
            set = (set+1) % 3
            chunk++
        }

        if not stopped {
            ; now start all four channels at the same tick, keeping the primed
            ; coarse/fine writes phase-locked on each speaker
            audio.start_channels($0f)

            ; steady state: reap the oldest queued chunk so its buffer set is free,
            ; read the next track into the set that was queued two chunks ago, decode
            ; and split it, and queue it on all channels.
            ; Only the coarse channels (0 and 1, one per speaker) are waited on:
            ; all four channels are phase-locked with identical period, length and
            ; start tick, so when a coarse chunk finishes its paired fine chunk and
            ; the whole buffer set are done too. Waiting on just these two instead
            ; of all four cuts per-iteration wait/scheduler overhead and issues the
            ; CMD_READ slightly earlier, which matters on a slow 68000 (A600).
            while chunk < TOTAL_TRACKS {
                if ctrl_c_pressed() {
                    stopped = true
                    break
                }
                audio.wait_queued(0)
                audio.wait_queued(1)
                if ctrl_c_pressed() {
                    stopped = true
                    break
                }
                err = trackdisk.read(staging, COMPRESSED_TRACK_BYTES,
                                          START_OFFSET + (chunk as long)*COMPRESSED_TRACK_BYTES)
                if err != 0 {
                    txt.print("\ntrack read error\n")
                    sys.exit(2)
                }
                txt.chrout('.')
                txt.flush()
                total_bytes += COMPRESSED_TRACK_BYTES
                decode_split_track(staging, coarse[set], fine[set])
                queue_chunk(set)
                set = (set+1) % 3
                chunk++
            }
        }

        if stopped {
            audio.stop_all()
            txt.print("\nStopped, played ")
            txt.print_l(total_bytes)
            txt.print(" bytes\n")
        } else {
            txt.nl()
            audio.wait_all()
            txt.print("done, played ")
            txt.print_l(total_bytes)
            txt.print(" bytes\n")
        }
    }

    sub ctrl_c_pressed() -> bool {
        return (exec.SetSignal(0, dos.SIGBREAKF_CTRL_C) & dos.SIGBREAKF_CTRL_C) != 0
    }

    sub queue_chunk(ubyte set) {
        ; queue this chunk on all 4 channels: coarse at vol 64 on channels
        ; 0+1 (left+right), fine at vol 1 on channels 3+2 (left+right).
        audio.play_queued(0, coarse[set], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME_COARSE, 1)
        audio.play_queued(3, fine[set], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME_FINE, 1)
        audio.play_queued(1, coarse[set], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME_COARSE, 1)
        audio.play_queued(2, fine[set], FRAMES_PER_TRACK as long, SAMPLE_RATE, VOLUME_FINE, 1)
    }

    ; Decode one compressed track (22 x 256-byte ADPCM blocks) into PCM and
    ; split the big-endian samples into coarse/fine buffers.
    sub decode_split_track(pointer input, pointer coarse_out, pointer fine_out) {
        ubyte block = 0
        pointer src = input
        while block < BLOCKS_PER_TRACK {
            adpcm.decode_block_mono(src, scratch)
            split14_be(scratch, coarse_out, fine_out, SAMPLES_PER_BLOCK)
            src += ADPCM_BLOCK_BYTES
            coarse_out += SAMPLES_PER_BLOCK
            fine_out += SAMPLES_PER_BLOCK
            block++
        }
    }

    ; split 16-bit signed big-endian mono samples into a coarse (upper 8 bits)
    ; and a fine ((sample & $ff) >> 2) stream, in separate buffers.
    asmsub split14_be(pointer raw @A0, ^^byte coarse @A1, ^^byte fine @A2, uword samples @D0) clobbers(D0,D1,D2,A0,A1,A2) {
        %asm {{
            tst.w   d0
            beq.s   .done
            subq.w  #1,d0
.split
            move.b  (a0)+,d2        ; high byte of BE sample
            move.b  (a0)+,d1        ; low byte of sample
            move.b  d2,(a1)+        ; coarse = top 8 bits
            lsr.b   #2,d1           ; fine = bits 2..7 of low byte
            move.b  d1,(a2)+
            dbra    d0,.split
.done
            rts
        }}
    }

    sub blurb() -> ubyte {
        txt.print("\n\nADPCM music streamer, requires 68020+ for smooth playback.\n")
        txt.print("Custom 14-bit Paula playback routine, maybe I'll do a proper AHI version later.\n")
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

sounddata {
    %option amiga_chipram     ; trackdisk DMA target and audio DMA target
    byte[main.COMPRESSED_TRACK_BYTES] staging
    byte[main.SAMPLES_PER_BLOCK*2] scratch
    byte[main.FRAMES_PER_TRACK] coarse0
    byte[main.FRAMES_PER_TRACK] fine0
    byte[main.FRAMES_PER_TRACK] coarse1
    byte[main.FRAMES_PER_TRACK] fine1
    byte[main.FRAMES_PER_TRACK] coarse2
    byte[main.FRAMES_PER_TRACK] fine2
}
