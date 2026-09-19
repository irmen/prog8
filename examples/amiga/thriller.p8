; Streaming PCM player for the Amiga (stock dos.library + audio.device, no asyncio needed):
; streams the raw signed 8-bit STEREO PCM file "thriller.pcm8" from disk and plays it in real time.
; The file is much too large to fit in memory, so it is played in chunks.
; Disk reads are synchronous (plain dos.Read), but they stay ahead of the audio via
; audio.device's 2-deep per-channel write queue: play_queued never waits for the current
; chunk to finish, so one chunk plays while the next is already queued and the device
; chains them with no gaps. Each chunk is exactly one FFS track (5632 bytes) so a read
; maps cleanly onto a single full-track disk read (minimal seeks, no partial-track re-reads).
; play_queued's auto-wait when the queue is full throttles the read loop to the audio speed for free.
; There are 3 buffer sets (device queue depth + 1), so a set being de-interleaved into is
; never one the DMA engine is still reading (chunk i-3 vs the i-1/i-2 still queued/playing).
; Left/right channels are split out of the interleaved data and played on audio channels 0 and 1.

%import dos
%import audio
%import textio

main {
    const long CHUNK_BYTES = 5632*4    ; one FFS floppy track (11 sectors x 512 bytes); read size == track size
    const uword SAMPLE_RATE = 8000   ; adjust this to match the sample rate of the file!
    const ubyte VOLUME = 64
    const ubyte NUM_SETS = 3         ; audio.device queue depth (2) + 1, see header

    ubyte[CHUNK_BYTES] r0            ; interleaved raw data staging buffers (plain memory)
    ubyte[CHUNK_BYTES] r1
    ubyte[CHUNK_BYTES] r2
    pointer[NUM_SETS] raw            ; rotation: raw set index -> raw staging buffer
    ^^byte[NUM_SETS] left            ; rotation: set index -> left chip ram sample buffer
    ^^byte[NUM_SETS] right           ; rotation: set index -> right chip ram sample buffer

    sub start() {
        raw[0] = &r0
        raw[1] = &r1
        raw[2] = &r2
        left[0] = &sounddata.left0
        left[1] = &sounddata.left1
        left[2] = &sounddata.left2
        right[0] = &sounddata.right0
        right[1] = &sounddata.right1
        right[2] = &sounddata.right2

        txt.print("streaming thriller.pcm8...\n")

        pointer file = dos.Open("thriller.pcm8", dos.MODE_OLDFILE)
        if file==0 {
            txt.print("cannot open thriller.pcm8\n")
            sys.exit(1)
        }
        defer void dos.Close(file)

        if not audio.init() {
            txt.print("cannot open audio.device\n")
            sys.exit(1)
        }
        defer audio.closedown()

        long total_bytes = 0
        long frames
        long n
        ubyte set = 0
        ; read, de-interleave and queue one chunk per pass, rotating through the buffer sets.
        ; play_queued blocks (by reaping the oldest write) only when the 2-deep device queue
        ; is full, so the loop is naturally paced by the audio and never overruns the buffers.
        do {
            n = dos.Read(file, raw[set], CHUNK_BYTES)
            if n>0 {
                txt.chrout('.')
                txt.flush()
                total_bytes += n
                frames = n>>1
                ; deinterleave's count param and its dbra loop counter are 16-bit, and
                ; play_queued caps one IO at 131072 bytes; both require frames <= 65535.
                assert (frames<=65535)
                deinterleave(raw[set], left[set], right[set], frames as uword)
                audio.play_queued(0, left[set], frames, SAMPLE_RATE, VOLUME, 1)
                audio.play_queued(1, right[set], frames, SAMPLE_RATE, VOLUME, 1)
                set = (set+1) % NUM_SETS
            }
        } until n<=0
        if n<0
            txt.print("\nread error!\n")

        txt.nl()
        audio.wait_all()
        txt.print("\ndone, played ")
        txt.print_l(total_bytes)
        txt.print(" bytes\n")
    }

    asmsub deinterleave(pointer raw @A0, ^^byte left @A1, ^^byte right @A2, uword frames @D0) clobbers(D0,A0,A1,A2) {
        %asm {{
            ; copy interleaved stereo frames to separate left/right buffers.
            ; not unrolled: the loop is paced by the ~1s audio.device queue, so a full
            ; 32KB chunk (~69ms here) is trivially within budget and floppy I/O dominates.
            tst.w   d0
            beq.s   .done             ; frames==0 -> nothing (also avoids the dbra-underflow trap)
            subq.w  #1,d0             ; frames-1 for dbra
.loop
            move.b  (a0)+,(a1)+       ; left sample byte
            move.b  (a0)+,(a2)+       ; right sample byte
            dbra    d0,.loop
.done
            rts
        }}
    }
}


sounddata {
    %option amiga_chipram     ; the audio DMA can only read from chip ram

    byte[main.CHUNK_BYTES/2] left0
    byte[main.CHUNK_BYTES/2] right0
    byte[main.CHUNK_BYTES/2] left1
    byte[main.CHUNK_BYTES/2] right1
    byte[main.CHUNK_BYTES/2] left2
    byte[main.CHUNK_BYTES/2] right2
}
