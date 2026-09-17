adpcm {

    ; IMA ADPCM decoder.  Supports mono and stereo streams. M68k 32 bits big endian version.
    ; https://wiki.multimedia.cx/index.php/IMA_ADPCM
    ; https://wiki.multimedia.cx/index.php/Microsoft_IMA_ADPCM

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

    %option merge, ignore_unused, private_symbols

    uword predict       ; decoded 16 bit pcm sample for first channel.
    uword predict_2     ; decoded 16 bit pcm sample for second channel.
    ubyte rowindex      ; current adpcm_deltas row number (0..88) for first channel.
    ubyte rowindex_2    ; current adpcm_deltas row number (0..88) for second channel.

    public sub decode_block_mono(pointer nibblesptr, pointer outptr) {
        ; Decodes one 256 byte block of mono adpcm data into a memory buffer.
        ; The input buffer (nibblesptr) must hold at least 256 bytes.
        ; The output buffer (outptr) must hold at least 1010 bytes.
        ; Decoded data is 16 bit mono PCM, 505 samples = 1010 bytes (little-endian).
        init(read_le_uword(nibblesptr), @(nibblesptr+2))
        outptr[0] = predict as ubyte
        outptr[1] = (predict >> 8) as ubyte
        outptr += 2
        nibblesptr += 4
        decode_block_mono_loop(nibblesptr, outptr)
    }

    private asmsub decode_block_mono_loop(pointer nibblesptr @A0, pointer outptr @A1) clobbers (D0, D1, D2, D3, D6, D7, A0, A1, A2) {
        ; Decodes the remaining 252 bytes (504 samples) of a mono block in 68000 assembly.
        ; On entry: A0 -> input nibbles, A1 -> output (already past the header sample).
        ; Keeps predict in D7 and rowindex in D6 for the whole loop.
        %asm {{
        lea     p8b_adpcm.p8v_deltas_table,a2
        moveq   #0,d6
        move.b  p8b_adpcm.p8v_rowindex,d6
        move.w  p8b_adpcm.p8v_predict,d7
        moveq   #0,d2
        move.w  #251,d2
.loop:
        move.b  (a0)+,d0
        bsr     p8b_adpcm.p8s_decode_byte_mono_reg
        dbra    d2,.loop
        move.w  d7,p8b_adpcm.p8v_predict
        move.b  d6,p8b_adpcm.p8v_rowindex
        rts
        }}
    }

    private asmsub decode_byte_mono_reg(ubyte value @D0, pointer outptr @A1) clobbers (D0, D1, D3, D6, D7, A1) {
        ; Decode both nibbles in one call for the first channel.
        ; In: D0.b = packed nibbles, D7 = predict, D6 = row, A1 = output.
        %asm {{
        moveq   #0,d3
        move.b  d0,d3
        and.w   #$000f,d0
        add.w   d0,d0
        moveq   #0,d1
        move.b  d6,d1
        lsl.w   #5,d1
        add.w   d0,d1
        move.w  32(a2,d1.w),d1
        add.w   d1,d7
        move.w  (a2,d0.w),d1
        moveq   #0,d0
        move.b  d6,d0
        add.w   d1,d0
        bmi.s   .clamp_low
        cmpi.w  #88,d0
        bgt.s   .clamp_high
        move.b  d0,d6
        bra.s   .write_low
.clamp_low:
        clr.b   d6
        bra.s   .write_low
.clamp_high:
        move.b  #88,d6
.write_low:
        move.b  d7,(a1)+
        move.w  d7,d1
        lsr.w   #8,d1
        move.b  d1,(a1)+
        move.w  d3,d0
        lsr.w   #4,d0
        add.w   d0,d0
        moveq   #0,d1
        move.b  d6,d1
        lsl.w   #5,d1
        add.w   d0,d1
        move.w  32(a2,d1.w),d1
        add.w   d1,d7
        move.w  (a2,d0.w),d1
        moveq   #0,d0
        move.b  d6,d0
        add.w   d1,d0
        bmi.s   .clamp_low2
        cmpi.w  #88,d0
        bgt.s   .clamp_high2
        move.b  d0,d6
        bra.s   .write_high
.clamp_low2:
        clr.b   d6
        bra.s   .write_high
.clamp_high2:
        move.b  #88,d6
.write_high:
        move.b  d7,(a1)+
        move.w  d7,d1
        lsr.w   #8,d1
        move.b  d1,(a1)+
        rts
        }}
    }

    public sub decode_block_stereo(pointer nibblesptr, pointer outptr) {
        ; Decodes one 256 byte block of stereo adpcm data into a memory buffer.
        ; The input buffer (nibblesptr) must hold at least 256 bytes.
        ; The output buffer (outptr) must hold at least 996 bytes.
        ; Decoded data is 16 bit stereo PCM, 498 samples = 996 bytes (little-endian, interleaved).
        init(read_le_uword(nibblesptr), @(nibblesptr+2))            ; left channel
        outptr[0] = predict as ubyte
        outptr[1] = (predict >> 8) as ubyte
        init_second(read_le_uword(nibblesptr+4), @(nibblesptr+6))   ; right channel
        outptr[2] = predict_2 as ubyte
        outptr[3] = (predict_2 >> 8) as ubyte
        outptr += 4
        nibblesptr += 8
        decode_block_stereo_loop(nibblesptr, outptr)
    }

    private asmsub decode_byte_stereo_reg(ubyte value @D0, pointer outptr @A1) clobbers (D0, D1, D3, D6, D7, A1) {
        ; Decode both nibbles in one call for the first stereo channel.
        ; In: D0.b = packed nibbles, D7 = predict, D6 = row, A1 = output.
        %asm {{
        moveq   #0,d3
        move.b  d0,d3
        and.w   #$000f,d0
        add.w   d0,d0
        moveq   #0,d1
        move.b  d6,d1
        lsl.w   #5,d1
        add.w   d0,d1
        move.w  32(a2,d1.w),d1
        add.w   d1,d7
        move.w  (a2,d0.w),d1
        moveq   #0,d0
        move.b  d6,d0
        add.w   d1,d0
        bmi.s   .clamp_low
        cmpi.w  #88,d0
        bgt.s   .clamp_high
        move.b  d0,d6
        bra.s   .write_low
.clamp_low:
        clr.b   d6
        bra.s   .write_low
.clamp_high:
        move.b  #88,d6
.write_low:
        move.b  d7,(a1)
        move.w  d7,d1
        lsr.w   #8,d1
        move.b  d1,(1,a1)
        move.w  d3,d0
        lsr.w   #4,d0
        add.w   d0,d0
        moveq   #0,d1
        move.b  d6,d1
        lsl.w   #5,d1
        add.w   d0,d1
        move.w  32(a2,d1.w),d1
        add.w   d1,d7
        move.w  (a2,d0.w),d1
        moveq   #0,d0
        move.b  d6,d0
        add.w   d1,d0
        bmi.s   .clamp_low2
        cmpi.w  #88,d0
        bgt.s   .clamp_high2
        move.b  d0,d6
        bra.s   .write_high
.clamp_low2:
        clr.b   d6
        bra.s   .write_high
.clamp_high2:
        move.b  #88,d6
.write_high:
        move.b  d7,(4,a1)
        move.w  d7,d1
        lsr.w   #8,d1
        move.b  d1,(5,a1)
        addq.l  #8,a1
        rts
        }}
    }

    private asmsub decode_byte_stereo_second_reg(ubyte value @D0, pointer outptr @A4) clobbers (D0, D1, D3, D4, D5, A4) {
        ; Decode both nibbles in one call for the second stereo channel.
        ; In: D0.b = packed nibbles, D5 = predict_2, D4 = row_2, A4 = output.
        %asm {{
        moveq   #0,d3
        move.b  d0,d3
        and.w   #$000f,d0
        add.w   d0,d0
        moveq   #0,d1
        move.b  d4,d1
        lsl.w   #5,d1
        add.w   d0,d1
        move.w  32(a2,d1.w),d1
        add.w   d1,d5
        move.w  (a2,d0.w),d1
        moveq   #0,d0
        move.b  d4,d0
        add.w   d1,d0
        bmi.s   .clamp_low
        cmpi.w  #88,d0
        bgt.s   .clamp_high
        move.b  d0,d4
        bra.s   .write_low
.clamp_low:
        clr.b   d4
        bra.s   .write_low
.clamp_high:
        move.b  #88,d4
.write_low:
        move.b  d5,(a4)
        move.w  d5,d1
        lsr.w   #8,d1
        move.b  d1,(1,a4)
        move.w  d3,d0
        lsr.w   #4,d0
        add.w   d0,d0
        moveq   #0,d1
        move.b  d4,d1
        lsl.w   #5,d1
        add.w   d0,d1
        move.w  32(a2,d1.w),d1
        add.w   d1,d5
        move.w  (a2,d0.w),d1
        moveq   #0,d0
        move.b  d4,d0
        add.w   d1,d0
        bmi.s   .clamp_low2
        cmpi.w  #88,d0
        bgt.s   .clamp_high2
        move.b  d0,d4
        bra.s   .write_high
.clamp_low2:
        clr.b   d4
        bra.s   .write_high
.clamp_high2:
        move.b  #88,d4
.write_high:
        move.b  d5,(4,a4)
        move.w  d5,d1
        lsr.w   #8,d1
        move.b  d1,(5,a4)
        addq.l  #8,a4
        rts
        }}
    }

    private asmsub decode_block_stereo_loop(pointer nibblesptr @A0, pointer outptr @A1) clobbers (D0, D1, D2, D3, D4, D5, D6, D7, A0, A1, A2, A3, A4) {
        ; Decodes the remaining 248 bytes (496 stereo samples) of a stereo block in 68000 assembly.
        ; On entry: A0 -> input nibbles after 8-byte header, A1 -> output after 4-byte header samples.
        ; Keeps left predict/row in D7/D6 and right predict/row in D5/D4.
        %asm {{
        lea     p8b_adpcm.p8v_deltas_table,a2
        moveq   #0,d4
        move.b  p8b_adpcm.p8v_rowindex_2,d4
        move.w  p8b_adpcm.p8v_predict_2,d5
        moveq   #0,d6
        move.b  p8b_adpcm.p8v_rowindex,d6
        move.w  p8b_adpcm.p8v_predict,d7
        lea     248(a0),a3
.outer:
        lea     2(a1),a4
        moveq   #3,d2
.lleft:
        move.b  (a0)+,d0
        bsr     p8b_adpcm.p8s_decode_byte_stereo_reg
        dbra    d2,.lleft
        moveq   #3,d2
.lright:
        move.b  (a0)+,d0
        bsr     p8b_adpcm.p8s_decode_byte_stereo_second_reg
        dbra    d2,.lright
        cmpa.l  a3,a0
        bne.s   .outer
        move.w  d7,p8b_adpcm.p8v_predict
        move.b  d6,p8b_adpcm.p8v_rowindex
        move.w  d5,p8b_adpcm.p8v_predict_2
        move.b  d4,p8b_adpcm.p8v_rowindex_2
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

    ; NOTE: this is a single combined table:
    ; the first 16 words are the row step lookup (t_rowdelta),
    ; the remaining 89*16 words are the delta table.
    ; The decode_block_*_loop asmsubs rely on this order (deltas read at 32(a0,d1.w)).
    word[] deltas_table = [
    ; row steps for the 16 possible nibbles (t_rowdelta):
    -1,-1,-1,-1,2,4,6,8,-1,-1,-1,-1,2,4,6,8,
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

}
