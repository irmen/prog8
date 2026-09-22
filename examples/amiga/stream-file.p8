; Streaming PCM player for the Amiga (stock dos.library + audio.device, no asyncio needed):
; streams the raw signed 8-bit MONO PCM file "music.pcm8" from disk and plays it in real time.
; The file is much too large to fit in memory, so it is played in chunks.
; Disk reads are synchronous (plain dos.Read), but they stay ahead of the audio via
; audio.device's 2-deep per-channel write queue: play_queued never waits for the current
; chunk to finish, so one chunk plays while the next is already queued and the device
; chains them with no gaps. play_queued's auto-wait when the queue is full throttles the
; read loop to the audio speed for free.
; There are 3 buffer sets (device queue depth + 1), so a set being filled into is
; never one the DMA engine is still reading (chunk i-3 vs the i-1/i-2 still queued/playing).
; The mono data is queued as-is on audio channels 0 and 1 (dual mono) for a centered output.

%import dos
%import audio
%import textio

main {
    const long CHUNK_BYTES = 5632*4     ; 4x one FFS track; max whole-track read size that still fits in the 32768-byte array limit
    const uword SAMPLE_RATE = 12000   ; adjust this to match the sample rate of the file!
    const ubyte VOLUME = 64
    const ubyte NUM_SETS = 3         ; audio.device queue depth (2) + 1, see header

    ^^byte[NUM_SETS] mono            ; rotation: set index -> chip ram sample buffer

    sub start() {
        mono[0] = &sounddata.mono0
        mono[1] = &sounddata.mono1
        mono[2] = &sounddata.mono2

        txt.print("streaming music.pcm8...\n")

        pointer file = dos.Open("music.pcm8", dos.MODE_OLDFILE)
        if file==0 {
            txt.print("cannot open\n")
            sys.exit(1)
        }
        defer void dos.Close(file)

        if not audio.init() {
            txt.print("cannot open audio.device\n")
            sys.exit(1)
        }
        defer audio.closedown()

        long total_bytes = 0
        long n
        ubyte set = 0
        ; read and queue one chunk per pass, rotating through the buffer sets.
        ; play_queued blocks (by reaping the oldest write) only when the 2-deep device queue
        ; is full, so the loop is naturally paced by the audio and never overruns the buffers.
        do {
            n = dos.Read(file, mono[set], CHUNK_BYTES)
            if n>0 {
                txt.chrout('.')
                txt.flush()
                total_bytes += n
                audio.play_queued(0, mono[set], n, SAMPLE_RATE, VOLUME, 1)
                audio.play_queued(1, mono[set], n, SAMPLE_RATE, VOLUME, 1)
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
}

sounddata {
    %option amiga_chipram     ; the audio DMA can only read from chip ram

    byte[main.CHUNK_BYTES] mono0
    byte[main.CHUNK_BYTES] mono1
    byte[main.CHUNK_BYTES] mono2
}
