adpcm {

    ; IMA ADPCM decoder, 68000 version, by Irmen de Jong, irmen@razorvine.net
    ; Supports mono and stereo streams.
    ; https://wiki.multimedia.cx/index.php/IMA_ADPCM
    ; https://wiki.multimedia.cx/index.php/Microsoft_IMA_ADPCM
    ;
    ; OUTPUT FORMAT: the decoded PCM samples are 16-bit signed values in BIG-ENDIAN
    ; byte order (the m68k native order, so the output can be fed straight to Paula
    ; or other m68k audio code without byte swapping). Mono output is a plain
    ; sequence of samples; stereo output is interleaved L,R,L,R,... pairs.
    ; If you need little-endian samples instead (for example to write a .wav file),
    ; you have to byte-swap every 16-bit sample yourself.

    ; IMA ADPCM encodes two 16-bit PCM audio samples in 1 byte (1 word per nibble)
    ; thus compressing the audio data by a factor of 4.
    ; The encoding precision is about 13 bits per sample so it's a lossy compression scheme.
    ;
    ; HOW TO CREATE IMA-ADPCM ENCODED AUDIO? Use sox or ffmpeg like so (example):
    ; $ sox --guard source.mp3 -r 8000 -c 1 -e ima-adpcm out.wav trim 01:27.50 00:09
    ; $ ffmpeg -i source.mp3 -ss 00:01:27.50 -to 00:01:36.50  -ar 8000 -ac 1 -c:a adpcm_ima_wav -block_size 256 -map_metadata -1 -bitexact out.wav
    ; And/or use a tool such as https://github.com/dbry/adpcm-xq  (make sure to set the correct block size, -b8)

    ; Possible pre-filter step to improve encoded sound quality:
    ; ffmpeg -i input.wav -af "highpass=f=50, lowpass=f=10000, acompressor=threshold=-12dB:ratio=3:attack=8:release=60, loudnorm=I=-16:TP=-1.5, aresample=22050" intermediate.wav
    ; (note that the filter parameters depend on the sample rate! Ask google what the correct parameters should be for different sample rates!)

    ;
    ; NOTE: sox may generate IMA-ADPCM files with a block size different than 256 bytes, which is not supported by this decoder. Use ffmpeg instead.
    ;
    ; NOTE: for speed reasons this implementation doesn't guard against clipping errors.
    ;       if the output sounds distorted, lower the volume of the source waveform to 80% and try again etc.


    ; IMA-ADPCM file data stream format:
    ; If the IMA data is mono, an individual chunk of data begins with the following preamble:
    ; bytes 0-1:   initial predictor (in little-endian format)
    ; byte 2:      initial index
    ; byte 3:      unknown, usually 0 and is probably reserved
    ; If the IMA data is stereo, a chunk begins with two preambles, one for the left audio channel and one for the right channel.
    ; (so we have 8 bytes of preamble).
    ; The remaining bytes in the chunk are the IMA nibbles. The first 4 bytes, or 8 nibbles,
    ; belong to the left channel and -if it's stereo- the next 4 bytes belong to the right channel.

    ; PERFORMANCE NOTES (plain 68000):
    ; The hot loops are built around two lookup tables and keep all state in registers:
    ; 1. deltas_table: 89*16 precomputed signed deltas (step/8 + step/4? + step/2? + step?,
    ;    negated when nibble bit 3 is set). One indexed word read per sample, no arithmetic.
    ; 2. next_state_table: the index update (index += t_index[nibble], clamped to 0..88)
    ;    is folded into a transition table, so there is NO clamping code in the hot loop
    ;    at all: no compares, no branches. Sentinel rows -1 and 89..96 make the table
    ;    total; they are unreachable from normal rows and from valid block headers.
    ; 3. The state register holds (rowindex+1)*32 and the next_state_table entries are
    ;    pre-multiplied by 32, so the state update is a single move.w (a3,d1.w),d6 with
    ;    no shifts. The SAME offset ((rowindex+1)*32 + nibble*2) also addresses the delta
    ;    table (biased by -32), so one add serves both table lookups per sample.
    ; 4. Input is read with move.l (4 bytes = 8 samples per load) and swap exposes both
    ;    16-bit halves; the mono loop needs only 63 dbra iterations per 256-byte block.
    ; 5. Output is stored big-endian with a single move.w per sample, no byte swapping.
    ;
    ; Compared to a small-table decoder (89-entry step table + 16-entry index table,
    ; e.g. Kalmalyzer's adpcm-68k): no per-sample delta computation, no index clamping
    ; (their spl/ext/and + cmp/bls sequence costs ~25 cycles/sample), one shared table
    ; offset instead of two separately computed ones, and no predictor clamping to
    ; [-32768,32767] (another ~35 cycles/sample in theirs; we deliberately wrap the
    ; 16-bit predictor instead, matching the 6502 decoder and ffmpeg on real files).
    ; Net result: ~64-78 cycles/sample here vs ~112+ cycles/sample there.
    ;
    ; NOT APPLIED (only useful on 68020/040/060; this code targets the plain 68000):
    ; - scaled indexing such as (a2,d1.l*4): would remove the explicit nibble*2 add.
    ; - a packed 8-byte transition entry (delta.l + nextstate.l fetched in one move.l):
    ;   halves the table reads but needs (a2,d1.l*8) scaled indexing and a ~12 KB table;
    ;   the extra memory bandwidth costs more on a 68000 than the saved read wins.
    ; - output batching (accumulate 2-4 samples in a register, one move.l store):
    ;   profitable on 040/060 where 32-bit shifts take a few cycles, but on 68000
    ;   lsl.l #8 costs 24 cycles, more than the stores it saves.
    ; - bfextu bitfield nibble extraction (68020+) instead of and/lsr sequences.
    ; - extb.l (68020+) for cheap byte extraction from the loaded longword.
    ; - deeper loop unrolling: on 68020+ the instruction cache makes this attractive,
    ;   on the cacheless 68000 it only saves the dbra (~10 cycles per 8 samples)
    ;   at a large code size cost.

    %option merge, ignore_unused, private_symbols

    uword predict       ; decoded 16 bit pcm sample for first channel.
    uword predict_2     ; decoded 16 bit pcm sample for second channel.
    ubyte rowindex      ; current adpcm_deltas row number (0..88) for first channel.
    ubyte rowindex_2    ; current adpcm_deltas row number (0..88) for second channel.

    public sub decode_block_mono(pointer nibblesptr, pointer outptr) {
        ; Decodes one 256 byte block of mono adpcm data into a memory buffer.
        ; The input buffer (nibblesptr) must hold at least 256 bytes.
        ; The output buffer (outptr) must hold at least 1010 bytes.
        ; Decoded data is 16 bit mono PCM, 505 samples = 1010 bytes (big-endian).
        init(read_le_uword(nibblesptr), @(nibblesptr+2))
        outptr[0] = (predict >> 8) as ubyte
        outptr[1] = predict as ubyte
        outptr += 2
        nibblesptr += 4
        decode_block_mono_loop(nibblesptr, outptr)
    }

    private asmsub decode_block_mono_loop(pointer nibblesptr @A0, pointer outptr @A1) clobbers (D0, D1, D2, D3, D4, D6, D7, A0, A1, A2, A3) {
        ; Decode 252 nibbles (63 longwords) using four-byte input unrolling.
        ; D7 = predict, D6 = (rowindex+1)*32, D4 = loaded longword, D3 = temp word.
        ; One table offset serves both the delta lookup and the pre-scaled next-state lookup.
        %asm {{
        lea     p8b_adpcm.p8v_deltas_table-32,a2
        lea     p8b_adpcm.p8v_next_state_table,a3
        moveq   #0,d6
        move.b  p8b_adpcm.p8v_rowindex,d6
        addq.w  #1,d6
        lsl.w   #5,d6          ; S = (rowindex + 1) * 32
        move.w  p8b_adpcm.p8v_predict,d7
        move.w  #62,d2
.loop:
        move.l  (a0)+,d4
        swap    d4
        move.w  d4,d3
        lsr.w   #8,d3
        ; byte 0
        move.b  d3,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)+
        move.w  d3,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)+
        move.w  d4,d3
        ; byte 1
        move.b  d3,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)+
        move.w  d3,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)+
        swap    d4
        move.w  d4,d3
        lsr.w   #8,d3
        ; byte 2
        move.b  d3,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)+
        move.w  d3,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)+
        move.w  d4,d3
        ; byte 3
        move.b  d3,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)+
        move.w  d3,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)+
        dbra    d2,.loop
        move.w  d7,p8b_adpcm.p8v_predict
        move.w  d6,d1
        lsr.w   #5,d1
        subq.w  #1,d1
        move.b  d1,p8b_adpcm.p8v_rowindex
        rts
        }}
    }

    public sub decode_block_stereo(pointer nibblesptr, pointer outptr) {
        ; Decodes one 256 byte block of stereo adpcm data into a memory buffer.
        ; The input buffer (nibblesptr) must hold at least 256 bytes.
        ; The output buffer (outptr) must hold at least 996 bytes.
        ; Decoded data is 16 bit stereo PCM, 498 samples = 996 bytes (big-endian,
        ; interleaved L,R,L,R,...).
        init(read_le_uword(nibblesptr), @(nibblesptr+2))
        outptr[0] = (predict >> 8) as ubyte
        outptr[1] = predict as ubyte
        init_second(read_le_uword(nibblesptr+4), @(nibblesptr+6))
        outptr[2] = (predict_2 >> 8) as ubyte
        outptr[3] = predict_2 as ubyte
        outptr += 4
        nibblesptr += 8
        decode_block_stereo_loop(nibblesptr, outptr)
    }

    private asmsub decode_block_stereo_loop(pointer nibblesptr @A0, pointer outptr @A1) clobbers (D0, D1, D2, D3, D4, D5, D6, D7, A0, A1, A2, A3, A4, A5) {
        ; Decode 248 nibbles in 31 outer iterations, each processing 4 left bytes
        ; then 4 right bytes.  D7/D6 = left predict/(rowindex+1)*32, D5/D3 = right,
        ; D4 = loaded longword, D2 = temp word, A5 = outer loop counter.
        ; Left samples go to (a1)/4(a1) and right samples to 2(a1)/6(a1) via a4,
        ; producing interleaved L,R output directly with no separate interleave pass.
        %asm {{
        lea     p8b_adpcm.p8v_deltas_table-32,a2
        lea     p8b_adpcm.p8v_next_state_table,a3
        moveq   #0,d3
        move.b  p8b_adpcm.p8v_rowindex_2,d3
        addq.w  #1,d3
        lsl.w   #5,d3          ; S_right = (rowindex_2 + 1) * 32
        move.w  p8b_adpcm.p8v_predict_2,d5
        moveq   #0,d6
        move.b  p8b_adpcm.p8v_rowindex,d6
        addq.w  #1,d6
        lsl.w   #5,d6          ; S_left = (rowindex + 1) * 32
        move.w  p8b_adpcm.p8v_predict,d7
        suba.l  a5,a5
        move.w  #31,a5
.loop:
        lea     2(a1),a4
        ; left group: 4 bytes -> 8 samples
        move.l  (a0)+,d4
        swap    d4
        move.w  d4,d2
        lsr.w   #8,d2
        ; byte 0
        move.b  d2,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)
        move.w  d2,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,4(a1)
        addq.l  #8,a1
        move.w  d4,d2
        ; byte 1
        move.b  d2,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)
        move.w  d2,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,4(a1)
        addq.l  #8,a1
        swap    d4
        move.w  d4,d2
        lsr.w   #8,d2
        ; byte 2
        move.b  d2,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)
        move.w  d2,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,4(a1)
        addq.l  #8,a1
        move.w  d4,d2
        ; byte 3
        move.b  d2,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,(a1)
        move.w  d2,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d6,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d7
        move.w  (a3,d1.w),d6
        move.w  d7,4(a1)
        addq.l  #8,a1
        ; right group: 4 bytes -> 8 samples
        move.l  (a0)+,d4
        swap    d4
        move.w  d4,d2
        lsr.w   #8,d2
        ; byte 0
        move.b  d2,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d3,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d5
        move.w  (a3,d1.w),d3
        move.w  d5,(a4)
        move.w  d2,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d3,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d5
        move.w  (a3,d1.w),d3
        move.w  d5,4(a4)
        addq.l  #8,a4
        move.w  d4,d2
        ; byte 1
        move.b  d2,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d3,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d5
        move.w  (a3,d1.w),d3
        move.w  d5,(a4)
        move.w  d2,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d3,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d5
        move.w  (a3,d1.w),d3
        move.w  d5,4(a4)
        addq.l  #8,a4
        swap    d4
        move.w  d4,d2
        lsr.w   #8,d2
        ; byte 2
        move.b  d2,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d3,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d5
        move.w  (a3,d1.w),d3
        move.w  d5,(a4)
        move.w  d2,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d3,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d5
        move.w  (a3,d1.w),d3
        move.w  d5,4(a4)
        addq.l  #8,a4
        move.w  d4,d2
        ; byte 3
        move.b  d2,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d3,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d5
        move.w  (a3,d1.w),d3
        move.w  d5,(a4)
        move.w  d2,d0
        lsr.w   #4,d0
        and.w   #$000f,d0
        add.w   d0,d0
        move.w  d3,d1
        add.w   d0,d1
        move.w  (a2,d1.w),d0
        add.w   d0,d5
        move.w  (a3,d1.w),d3
        move.w  d5,4(a4)
        addq.l  #8,a4
        subq.l  #1,a5
        cmpa.l  #0,a5          ; subq on an address register does not set flags
        bne     .loop
        move.w  d7,p8b_adpcm.p8v_predict
        move.w  d6,d1
        lsr.w   #5,d1
        subq.w  #1,d1
        move.b  d1,p8b_adpcm.p8v_rowindex
        move.w  d5,p8b_adpcm.p8v_predict_2
        move.w  d3,d1
        lsr.w   #5,d1
        subq.w  #1,d1
        move.b  d1,p8b_adpcm.p8v_rowindex_2
        rts
        }}
    }

    sub read_le_uword(pointer addr) -> uword {
        ; read a 16-bit little-endian value from memory (ADPCM wav data is LE)
        return ((addr[1] as uword) << 8) | addr[0] as uword
    }

    sub init(uword startPredict, ubyte startIndex) {
        ; initialize first decoding channel.
        predict = startPredict
        rowindex = startIndex
    }

    sub init_second(uword startPredict_2, ubyte startIndex_2) {
        ; initialize second decoding channel.
        predict_2 = startPredict_2
        rowindex_2 = startIndex_2
    }

    ; IMA ADPCM delta table: 89 step sizes * 16 nibbles = 1424 signed 16-bit values.
    ; Delta = step/8 + (bit2? step) + (bit1? step/2) + (bit0? step/4), negated if nibble bit 3 set.
    ; Stored as interleaved signed 16-bit words; index into it is (index<<4)|nibble.
    ; Note: deltas for large steps exceed +32767; they are stored two's-complement
    ; (e.g. +36862 as -28674). The 16-bit wrapping add in the decoder treats these the
    ; same as their unsigned representation.
    word[] deltas_table = [
    0, 1, 3, 4, 7, 8, 10, 11, 0, -1, -3, -4, -7, -8, -10, -11,
    1, 3, 5, 7, 9, 11, 13, 15, -1, -3, -5, -7, -9, -11, -13, -15,
    1, 3, 5, 7, 10, 12, 14, 16, -1, -3, -5, -7, -10, -12, -14, -16,
    1, 3, 6, 8, 11, 13, 16, 18, -1, -3, -6, -8, -11, -13, -16, -18,
    1, 3, 6, 8, 12, 14, 17, 19, -1, -3, -6, -8, -12, -14, -17, -19,
    1, 4, 7, 10, 13, 16, 19, 22, -1, -4, -7, -10, -13, -16, -19, -22,
    1, 4, 7, 10, 14, 17, 20, 23, -1, -4, -7, -10, -14, -17, -20, -23,
    1, 4, 8, 11, 15, 18, 22, 25, -1, -4, -8, -11, -15, -18, -22, -25,
    2, 6, 10, 14, 18, 22, 26, 30, -2, -6, -10, -14, -18, -22, -26, -30,
    2, 6, 10, 14, 19, 23, 27, 31, -2, -6, -10, -14, -19, -23, -27, -31,
    2, 6, 11, 15, 21, 25, 30, 34, -2, -6, -11, -15, -21, -25, -30, -34,
    2, 7, 12, 17, 23, 28, 33, 38, -2, -7, -12, -17, -23, -28, -33, -38,
    2, 7, 13, 18, 25, 30, 36, 41, -2, -7, -13, -18, -25, -30, -36, -41,
    3, 9, 15, 21, 28, 34, 40, 46, -3, -9, -15, -21, -28, -34, -40, -46,
    3, 10, 17, 24, 31, 38, 45, 52, -3, -10, -17, -24, -31, -38, -45, -52,
    3, 10, 18, 25, 34, 41, 49, 56, -3, -10, -18, -25, -34, -41, -49, -56,
    4, 12, 21, 29, 38, 46, 55, 63, -4, -12, -21, -29, -38, -46, -55, -63,
    4, 13, 22, 31, 41, 50, 59, 68, -4, -13, -22, -31, -41, -50, -59, -68,
    5, 15, 25, 35, 46, 56, 66, 76, -5, -15, -25, -35, -46, -56, -66, -76,
    5, 16, 27, 38, 50, 61, 72, 83, -5, -16, -27, -38, -50, -61, -72, -83,
    6, 18, 31, 43, 56, 68, 81, 93, -6, -18, -31, -43, -56, -68, -81, -93,
    6, 19, 33, 46, 61, 74, 88, 101, -6, -19, -33, -46, -61, -74, -88, -101,
    7, 22, 37, 52, 67, 82, 97, 112, -7, -22, -37, -52, -67, -82, -97, -112,
    8, 24, 41, 57, 74, 90, 107, 123, -8, -24, -41, -57, -74, -90, -107, -123,
    9, 27, 45, 63, 82, 100, 118, 136, -9, -27, -45, -63, -82, -100, -118, -136,
    10, 30, 50, 70, 90, 110, 130, 150, -10, -30, -50, -70, -90, -110, -130, -150,
    11, 33, 55, 77, 99, 121, 143, 165, -11, -33, -55, -77, -99, -121, -143, -165,
    12, 36, 60, 84, 109, 133, 157, 181, -12, -36, -60, -84, -109, -133, -157, -181,
    13, 39, 66, 92, 120, 146, 173, 199, -13, -39, -66, -92, -120, -146, -173, -199,
    14, 43, 73, 102, 132, 161, 191, 220, -14, -43, -73, -102, -132, -161, -191, -220,
    16, 48, 81, 113, 146, 178, 211, 243, -16, -48, -81, -113, -146, -178, -211, -243,
    17, 52, 88, 123, 160, 195, 231, 266, -17, -52, -88, -123, -160, -195, -231, -266,
    19, 58, 97, 136, 176, 215, 254, 293, -19, -58, -97, -136, -176, -215, -254, -293,
    21, 64, 107, 150, 194, 237, 280, 323, -21, -64, -107, -150, -194, -237, -280, -323,
    23, 70, 118, 165, 213, 260, 308, 355, -23, -70, -118, -165, -213, -260, -308, -355,
    26, 78, 130, 182, 235, 287, 339, 391, -26, -78, -130, -182, -235, -287, -339, -391,
    28, 85, 143, 200, 258, 315, 373, 430, -28, -85, -143, -200, -258, -315, -373, -430,
    31, 94, 157, 220, 284, 347, 410, 473, -31, -94, -157, -220, -284, -347, -410, -473,
    34, 103, 173, 242, 313, 382, 452, 521, -34, -103, -173, -242, -313, -382, -452, -521,
    38, 114, 191, 267, 345, 421, 498, 574, -38, -114, -191, -267, -345, -421, -498, -574,
    42, 126, 210, 294, 379, 463, 547, 631, -42, -126, -210, -294, -379, -463, -547, -631,
    46, 138, 231, 323, 417, 509, 602, 694, -46, -138, -231, -323, -417, -509, -602, -694,
    51, 153, 255, 357, 459, 561, 663, 765, -51, -153, -255, -357, -459, -561, -663, -765,
    56, 168, 280, 392, 505, 617, 729, 841, -56, -168, -280, -392, -505, -617, -729, -841,
    61, 184, 308, 431, 555, 678, 802, 925, -61, -184, -308, -431, -555, -678, -802, -925,
    68, 204, 340, 476, 612, 748, 884, 1020, -68, -204, -340, -476, -612, -748, -884, -1020,
    74, 223, 373, 522, 672, 821, 971, 1120, -74, -223, -373, -522, -672, -821, -971, -1120,
    82, 246, 411, 575, 740, 904, 1069, 1233, -82, -246, -411, -575, -740, -904, -1069, -1233,
    90, 271, 452, 633, 814, 995, 1176, 1357, -90, -271, -452, -633, -814, -995, -1176, -1357,
    99, 298, 497, 696, 895, 1094, 1293, 1492, -99, -298, -497, -696, -895, -1094, -1293, -1492,
    109, 328, 547, 766, 985, 1204, 1423, 1642, -109, -328, -547, -766, -985, -1204, -1423, -1642,
    120, 360, 601, 841, 1083, 1323, 1564, 1804, -120, -360, -601, -841, -1083, -1323, -1564, -1804,
    132, 397, 662, 927, 1192, 1457, 1722, 1987, -132, -397, -662, -927, -1192, -1457, -1722, -1987,
    145, 436, 728, 1019, 1311, 1602, 1894, 2185, -145, -436, -728, -1019, -1311, -1602, -1894, -2185,
    160, 480, 801, 1121, 1442, 1762, 2083, 2403, -160, -480, -801, -1121, -1442, -1762, -2083, -2403,
    176, 528, 881, 1233, 1587, 1939, 2292, 2644, -176, -528, -881, -1233, -1587, -1939, -2292, -2644,
    194, 582, 970, 1358, 1746, 2134, 2522, 2910, -194, -582, -970, -1358, -1746, -2134, -2522, -2910,
    213, 639, 1066, 1492, 1920, 2346, 2773, 3199, -213, -639, -1066, -1492, -1920, -2346, -2773, -3199,
    234, 703, 1173, 1642, 2112, 2581, 3051, 3520, -234, -703, -1173, -1642, -2112, -2581, -3051, -3520,
    258, 774, 1291, 1807, 2324, 2840, 3357, 3873, -258, -774, -1291, -1807, -2324, -2840, -3357, -3873,
    284, 852, 1420, 1988, 2556, 3124, 3692, 4260, -284, -852, -1420, -1988, -2556, -3124, -3692, -4260,
    312, 936, 1561, 2185, 2811, 3435, 4060, 4684, -312, -936, -1561, -2185, -2811, -3435, -4060, -4684,
    343, 1030, 1717, 2404, 3092, 3779, 4466, 5153, -343, -1030, -1717, -2404, -3092, -3779, -4466, -5153,
    378, 1134, 1890, 2646, 3402, 4158, 4914, 5670, -378, -1134, -1890, -2646, -3402, -4158, -4914, -5670,
    415, 1246, 2078, 2909, 3742, 4573, 5405, 6236, -415, -1246, -2078, -2909, -3742, -4573, -5405, -6236,
    457, 1372, 2287, 3202, 4117, 5032, 5947, 6862, -457, -1372, -2287, -3202, -4117, -5032, -5947, -6862,
    503, 1509, 2516, 3522, 4529, 5535, 6542, 7548, -503, -1509, -2516, -3522, -4529, -5535, -6542, -7548,
    553, 1660, 2767, 3874, 4981, 6088, 7195, 8302, -553, -1660, -2767, -3874, -4981, -6088, -7195, -8302,
    608, 1825, 3043, 4260, 5479, 6696, 7914, 9131, -608, -1825, -3043, -4260, -5479, -6696, -7914, -9131,
    669, 2008, 3348, 4687, 6027, 7366, 8706, 10045, -669, -2008, -3348, -4687, -6027, -7366, -8706, -10045,
    736, 2209, 3683, 5156, 6630, 8103, 9577, 11050, -736, -2209, -3683, -5156, -6630, -8103, -9577, -11050,
    810, 2431, 4052, 5673, 7294, 8915, 10536, 12157, -810, -2431, -4052, -5673, -7294, -8915, -10536, -12157,
    891, 2674, 4457, 6240, 8023, 9806, 11589, 13372, -891, -2674, -4457, -6240, -8023, -9806, -11589, -13372,
    980, 2941, 4902, 6863, 8825, 10786, 12747, 14708, -980, -2941, -4902, -6863, -8825, -10786, -12747, -14708,
    1078, 3235, 5393, 7550, 9708, 11865, 14023, 16180, -1078, -3235, -5393, -7550, -9708, -11865, -14023, -16180,
    1186, 3559, 5932, 8305, 10679, 13052, 15425, 17798, -1186, -3559, -5932, -8305, -10679, -13052, -15425, -17798,
    1305, 3915, 6526, 9136, 11747, 14357, 16968, 19578, -1305, -3915, -6526, -9136, -11747, -14357, -16968, -19578,
    1435, 4306, 7178, 10049, 12922, 15793, 18665, 21536, -1435, -4306, -7178, -10049, -12922, -15793, -18665, -21536,
    1579, 4737, 7896, 11054, 14214, 17372, 20531, 23689, -1579, -4737, -7896, -11054, -14214, -17372, -20531, -23689,
    1737, 5211, 8686, 12160, 15636, 19110, 22585, 26059, -1737, -5211, -8686, -12160, -15636, -19110, -22585, -26059,
    1911, 5733, 9555, 13377, 17200, 21022, 24844, 28666, -1911, -5733, -9555, -13377, -17200, -21022, -24844, -28666,
    2102, 6306, 10511, 14715, 18920, 23124, 27329, 31533, -2102, -6306, -10511, -14715, -18920, -23124, -27329, -31533,
    2312, 6937, 11562, 16187, 20812, 25437, 30062, -30849, -2312, -6937, -11562, -16187, -20812, -25437, -30062, 30849,
    2543, 7630, 12718, 17805, 22893, 27980, -32468, -27381, -2543, -7630, -12718, -17805, -22893, -27980, 32468, 27381,
    2798, 8394, 13990, 19586, 25183, 30779, -29161, -23565, -2798, -8394, -13990, -19586, -25183, -30779, 29161, 23565,
    3077, 9232, 15388, 21543, 27700, -31681, -25525, -19370, -3077, -9232, -15388, -21543, -27700, 31681, 25525, 19370,
    3385, 10156, 16928, 23699, 30471, -28294, -21522, -14751, -3385, -10156, -16928, -23699, -30471, 28294, 21522, 14751,
    3724, 11172, 18621, 26069, -32018, -24570, -17121, -9673, -3724, -11172, -18621, -26069, 32018, 24570, 17121, 9673,
    4095, 12286, 20478, 28669, -28674, -20483, -12291, -4100, -4095, -12286, -20478, -28669, 28674, 20483, 12291, 4100
    ]

    ; Pre-scaled next-state table for fast-path ADPCM decoding.
    ; 98 logical rows (-1 sentinel low, 0..88 normal, 89..96 sentinel high) x 16 nibbles.
    ; Each word is the next state's register value directly: (next rowindex + 1) * 32.
    ; Indexed by the same offset as the delta lookup: (rowindex+1)*32 + nibble*2.
    ; Sentinel rows self-loop and are unreachable from normal rows.
    word[] next_state_table = [
        ; logical row -1
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        ; logical row 0
        32, 32, 32, 32, 96, 160, 224, 288, 32, 32, 32, 32, 96, 160, 224, 288,
        ; logical row 1
        32, 32, 32, 32, 128, 192, 256, 320, 32, 32, 32, 32, 128, 192, 256, 320,
        ; logical row 2
        64, 64, 64, 64, 160, 224, 288, 352, 64, 64, 64, 64, 160, 224, 288, 352,
        ; logical row 3
        96, 96, 96, 96, 192, 256, 320, 384, 96, 96, 96, 96, 192, 256, 320, 384,
        ; logical row 4
        128, 128, 128, 128, 224, 288, 352, 416, 128, 128, 128, 128, 224, 288, 352, 416,
        ; logical row 5
        160, 160, 160, 160, 256, 320, 384, 448, 160, 160, 160, 160, 256, 320, 384, 448,
        ; logical row 6
        192, 192, 192, 192, 288, 352, 416, 480, 192, 192, 192, 192, 288, 352, 416, 480,
        ; logical row 7
        224, 224, 224, 224, 320, 384, 448, 512, 224, 224, 224, 224, 320, 384, 448, 512,
        ; logical row 8
        256, 256, 256, 256, 352, 416, 480, 544, 256, 256, 256, 256, 352, 416, 480, 544,
        ; logical row 9
        288, 288, 288, 288, 384, 448, 512, 576, 288, 288, 288, 288, 384, 448, 512, 576,
        ; logical row 10
        320, 320, 320, 320, 416, 480, 544, 608, 320, 320, 320, 320, 416, 480, 544, 608,
        ; logical row 11
        352, 352, 352, 352, 448, 512, 576, 640, 352, 352, 352, 352, 448, 512, 576, 640,
        ; logical row 12
        384, 384, 384, 384, 480, 544, 608, 672, 384, 384, 384, 384, 480, 544, 608, 672,
        ; logical row 13
        416, 416, 416, 416, 512, 576, 640, 704, 416, 416, 416, 416, 512, 576, 640, 704,
        ; logical row 14
        448, 448, 448, 448, 544, 608, 672, 736, 448, 448, 448, 448, 544, 608, 672, 736,
        ; logical row 15
        480, 480, 480, 480, 576, 640, 704, 768, 480, 480, 480, 480, 576, 640, 704, 768,
        ; logical row 16
        512, 512, 512, 512, 608, 672, 736, 800, 512, 512, 512, 512, 608, 672, 736, 800,
        ; logical row 17
        544, 544, 544, 544, 640, 704, 768, 832, 544, 544, 544, 544, 640, 704, 768, 832,
        ; logical row 18
        576, 576, 576, 576, 672, 736, 800, 864, 576, 576, 576, 576, 672, 736, 800, 864,
        ; logical row 19
        608, 608, 608, 608, 704, 768, 832, 896, 608, 608, 608, 608, 704, 768, 832, 896,
        ; logical row 20
        640, 640, 640, 640, 736, 800, 864, 928, 640, 640, 640, 640, 736, 800, 864, 928,
        ; logical row 21
        672, 672, 672, 672, 768, 832, 896, 960, 672, 672, 672, 672, 768, 832, 896, 960,
        ; logical row 22
        704, 704, 704, 704, 800, 864, 928, 992, 704, 704, 704, 704, 800, 864, 928, 992,
        ; logical row 23
        736, 736, 736, 736, 832, 896, 960, 1024, 736, 736, 736, 736, 832, 896, 960, 1024,
        ; logical row 24
        768, 768, 768, 768, 864, 928, 992, 1056, 768, 768, 768, 768, 864, 928, 992, 1056,
        ; logical row 25
        800, 800, 800, 800, 896, 960, 1024, 1088, 800, 800, 800, 800, 896, 960, 1024, 1088,
        ; logical row 26
        832, 832, 832, 832, 928, 992, 1056, 1120, 832, 832, 832, 832, 928, 992, 1056, 1120,
        ; logical row 27
        864, 864, 864, 864, 960, 1024, 1088, 1152, 864, 864, 864, 864, 960, 1024, 1088, 1152,
        ; logical row 28
        896, 896, 896, 896, 992, 1056, 1120, 1184, 896, 896, 896, 896, 992, 1056, 1120, 1184,
        ; logical row 29
        928, 928, 928, 928, 1024, 1088, 1152, 1216, 928, 928, 928, 928, 1024, 1088, 1152, 1216,
        ; logical row 30
        960, 960, 960, 960, 1056, 1120, 1184, 1248, 960, 960, 960, 960, 1056, 1120, 1184, 1248,
        ; logical row 31
        992, 992, 992, 992, 1088, 1152, 1216, 1280, 992, 992, 992, 992, 1088, 1152, 1216, 1280,
        ; logical row 32
        1024, 1024, 1024, 1024, 1120, 1184, 1248, 1312, 1024, 1024, 1024, 1024, 1120, 1184, 1248, 1312,
        ; logical row 33
        1056, 1056, 1056, 1056, 1152, 1216, 1280, 1344, 1056, 1056, 1056, 1056, 1152, 1216, 1280, 1344,
        ; logical row 34
        1088, 1088, 1088, 1088, 1184, 1248, 1312, 1376, 1088, 1088, 1088, 1088, 1184, 1248, 1312, 1376,
        ; logical row 35
        1120, 1120, 1120, 1120, 1216, 1280, 1344, 1408, 1120, 1120, 1120, 1120, 1216, 1280, 1344, 1408,
        ; logical row 36
        1152, 1152, 1152, 1152, 1248, 1312, 1376, 1440, 1152, 1152, 1152, 1152, 1248, 1312, 1376, 1440,
        ; logical row 37
        1184, 1184, 1184, 1184, 1280, 1344, 1408, 1472, 1184, 1184, 1184, 1184, 1280, 1344, 1408, 1472,
        ; logical row 38
        1216, 1216, 1216, 1216, 1312, 1376, 1440, 1504, 1216, 1216, 1216, 1216, 1312, 1376, 1440, 1504,
        ; logical row 39
        1248, 1248, 1248, 1248, 1344, 1408, 1472, 1536, 1248, 1248, 1248, 1248, 1344, 1408, 1472, 1536,
        ; logical row 40
        1280, 1280, 1280, 1280, 1376, 1440, 1504, 1568, 1280, 1280, 1280, 1280, 1376, 1440, 1504, 1568,
        ; logical row 41
        1312, 1312, 1312, 1312, 1408, 1472, 1536, 1600, 1312, 1312, 1312, 1312, 1408, 1472, 1536, 1600,
        ; logical row 42
        1344, 1344, 1344, 1344, 1440, 1504, 1568, 1632, 1344, 1344, 1344, 1344, 1440, 1504, 1568, 1632,
        ; logical row 43
        1376, 1376, 1376, 1376, 1472, 1536, 1600, 1664, 1376, 1376, 1376, 1376, 1472, 1536, 1600, 1664,
        ; logical row 44
        1408, 1408, 1408, 1408, 1504, 1568, 1632, 1696, 1408, 1408, 1408, 1408, 1504, 1568, 1632, 1696,
        ; logical row 45
        1440, 1440, 1440, 1440, 1536, 1600, 1664, 1728, 1440, 1440, 1440, 1440, 1536, 1600, 1664, 1728,
        ; logical row 46
        1472, 1472, 1472, 1472, 1568, 1632, 1696, 1760, 1472, 1472, 1472, 1472, 1568, 1632, 1696, 1760,
        ; logical row 47
        1504, 1504, 1504, 1504, 1600, 1664, 1728, 1792, 1504, 1504, 1504, 1504, 1600, 1664, 1728, 1792,
        ; logical row 48
        1536, 1536, 1536, 1536, 1632, 1696, 1760, 1824, 1536, 1536, 1536, 1536, 1632, 1696, 1760, 1824,
        ; logical row 49
        1568, 1568, 1568, 1568, 1664, 1728, 1792, 1856, 1568, 1568, 1568, 1568, 1664, 1728, 1792, 1856,
        ; logical row 50
        1600, 1600, 1600, 1600, 1696, 1760, 1824, 1888, 1600, 1600, 1600, 1600, 1696, 1760, 1824, 1888,
        ; logical row 51
        1632, 1632, 1632, 1632, 1728, 1792, 1856, 1920, 1632, 1632, 1632, 1632, 1728, 1792, 1856, 1920,
        ; logical row 52
        1664, 1664, 1664, 1664, 1760, 1824, 1888, 1952, 1664, 1664, 1664, 1664, 1760, 1824, 1888, 1952,
        ; logical row 53
        1696, 1696, 1696, 1696, 1792, 1856, 1920, 1984, 1696, 1696, 1696, 1696, 1792, 1856, 1920, 1984,
        ; logical row 54
        1728, 1728, 1728, 1728, 1824, 1888, 1952, 2016, 1728, 1728, 1728, 1728, 1824, 1888, 1952, 2016,
        ; logical row 55
        1760, 1760, 1760, 1760, 1856, 1920, 1984, 2048, 1760, 1760, 1760, 1760, 1856, 1920, 1984, 2048,
        ; logical row 56
        1792, 1792, 1792, 1792, 1888, 1952, 2016, 2080, 1792, 1792, 1792, 1792, 1888, 1952, 2016, 2080,
        ; logical row 57
        1824, 1824, 1824, 1824, 1920, 1984, 2048, 2112, 1824, 1824, 1824, 1824, 1920, 1984, 2048, 2112,
        ; logical row 58
        1856, 1856, 1856, 1856, 1952, 2016, 2080, 2144, 1856, 1856, 1856, 1856, 1952, 2016, 2080, 2144,
        ; logical row 59
        1888, 1888, 1888, 1888, 1984, 2048, 2112, 2176, 1888, 1888, 1888, 1888, 1984, 2048, 2112, 2176,
        ; logical row 60
        1920, 1920, 1920, 1920, 2016, 2080, 2144, 2208, 1920, 1920, 1920, 1920, 2016, 2080, 2144, 2208,
        ; logical row 61
        1952, 1952, 1952, 1952, 2048, 2112, 2176, 2240, 1952, 1952, 1952, 1952, 2048, 2112, 2176, 2240,
        ; logical row 62
        1984, 1984, 1984, 1984, 2080, 2144, 2208, 2272, 1984, 1984, 1984, 1984, 2080, 2144, 2208, 2272,
        ; logical row 63
        2016, 2016, 2016, 2016, 2112, 2176, 2240, 2304, 2016, 2016, 2016, 2016, 2112, 2176, 2240, 2304,
        ; logical row 64
        2048, 2048, 2048, 2048, 2144, 2208, 2272, 2336, 2048, 2048, 2048, 2048, 2144, 2208, 2272, 2336,
        ; logical row 65
        2080, 2080, 2080, 2080, 2176, 2240, 2304, 2368, 2080, 2080, 2080, 2080, 2176, 2240, 2304, 2368,
        ; logical row 66
        2112, 2112, 2112, 2112, 2208, 2272, 2336, 2400, 2112, 2112, 2112, 2112, 2208, 2272, 2336, 2400,
        ; logical row 67
        2144, 2144, 2144, 2144, 2240, 2304, 2368, 2432, 2144, 2144, 2144, 2144, 2240, 2304, 2368, 2432,
        ; logical row 68
        2176, 2176, 2176, 2176, 2272, 2336, 2400, 2464, 2176, 2176, 2176, 2176, 2272, 2336, 2400, 2464,
        ; logical row 69
        2208, 2208, 2208, 2208, 2304, 2368, 2432, 2496, 2208, 2208, 2208, 2208, 2304, 2368, 2432, 2496,
        ; logical row 70
        2240, 2240, 2240, 2240, 2336, 2400, 2464, 2528, 2240, 2240, 2240, 2240, 2336, 2400, 2464, 2528,
        ; logical row 71
        2272, 2272, 2272, 2272, 2368, 2432, 2496, 2560, 2272, 2272, 2272, 2272, 2368, 2432, 2496, 2560,
        ; logical row 72
        2304, 2304, 2304, 2304, 2400, 2464, 2528, 2592, 2304, 2304, 2304, 2304, 2400, 2464, 2528, 2592,
        ; logical row 73
        2336, 2336, 2336, 2336, 2432, 2496, 2560, 2624, 2336, 2336, 2336, 2336, 2432, 2496, 2560, 2624,
        ; logical row 74
        2368, 2368, 2368, 2368, 2464, 2528, 2592, 2656, 2368, 2368, 2368, 2368, 2464, 2528, 2592, 2656,
        ; logical row 75
        2400, 2400, 2400, 2400, 2496, 2560, 2624, 2688, 2400, 2400, 2400, 2400, 2496, 2560, 2624, 2688,
        ; logical row 76
        2432, 2432, 2432, 2432, 2528, 2592, 2656, 2720, 2432, 2432, 2432, 2432, 2528, 2592, 2656, 2720,
        ; logical row 77
        2464, 2464, 2464, 2464, 2560, 2624, 2688, 2752, 2464, 2464, 2464, 2464, 2560, 2624, 2688, 2752,
        ; logical row 78
        2496, 2496, 2496, 2496, 2592, 2656, 2720, 2784, 2496, 2496, 2496, 2496, 2592, 2656, 2720, 2784,
        ; logical row 79
        2528, 2528, 2528, 2528, 2624, 2688, 2752, 2816, 2528, 2528, 2528, 2528, 2624, 2688, 2752, 2816,
        ; logical row 80
        2560, 2560, 2560, 2560, 2656, 2720, 2784, 2848, 2560, 2560, 2560, 2560, 2656, 2720, 2784, 2848,
        ; logical row 81
        2592, 2592, 2592, 2592, 2688, 2752, 2816, 2848, 2592, 2592, 2592, 2592, 2688, 2752, 2816, 2848,
        ; logical row 82
        2624, 2624, 2624, 2624, 2720, 2784, 2848, 2848, 2624, 2624, 2624, 2624, 2720, 2784, 2848, 2848,
        ; logical row 83
        2656, 2656, 2656, 2656, 2752, 2816, 2848, 2848, 2656, 2656, 2656, 2656, 2752, 2816, 2848, 2848,
        ; logical row 84
        2688, 2688, 2688, 2688, 2784, 2848, 2848, 2848, 2688, 2688, 2688, 2688, 2784, 2848, 2848, 2848,
        ; logical row 85
        2720, 2720, 2720, 2720, 2816, 2848, 2848, 2848, 2720, 2720, 2720, 2720, 2816, 2848, 2848, 2848,
        ; logical row 86
        2752, 2752, 2752, 2752, 2848, 2848, 2848, 2848, 2752, 2752, 2752, 2752, 2848, 2848, 2848, 2848,
        ; logical row 87
        2784, 2784, 2784, 2784, 2848, 2848, 2848, 2848, 2784, 2784, 2784, 2784, 2848, 2848, 2848, 2848,
        ; logical row 88
        2816, 2816, 2816, 2816, 2848, 2848, 2848, 2848, 2816, 2816, 2816, 2816, 2848, 2848, 2848, 2848,
        ; logical row 89
        2880, 2880, 2880, 2880, 2880, 2880, 2880, 2880, 2880, 2880, 2880, 2880, 2880, 2880, 2880, 2880,
        ; logical row 90
        2912, 2912, 2912, 2912, 2912, 2912, 2912, 2912, 2912, 2912, 2912, 2912, 2912, 2912, 2912, 2912,
        ; logical row 91
        2944, 2944, 2944, 2944, 2944, 2944, 2944, 2944, 2944, 2944, 2944, 2944, 2944, 2944, 2944, 2944,
        ; logical row 92
        2976, 2976, 2976, 2976, 2976, 2976, 2976, 2976, 2976, 2976, 2976, 2976, 2976, 2976, 2976, 2976,
        ; logical row 93
        3008, 3008, 3008, 3008, 3008, 3008, 3008, 3008, 3008, 3008, 3008, 3008, 3008, 3008, 3008, 3008,
        ; logical row 94
        3040, 3040, 3040, 3040, 3040, 3040, 3040, 3040, 3040, 3040, 3040, 3040, 3040, 3040, 3040, 3040,
        ; logical row 95
        3072, 3072, 3072, 3072, 3072, 3072, 3072, 3072, 3072, 3072, 3072, 3072, 3072, 3072, 3072, 3072,
        ; logical row 96
        3104, 3104, 3104, 3104, 3104, 3104, 3104, 3104, 3104, 3104, 3104, 3104, 3104, 3104, 3104, 3104,
    ]

}
