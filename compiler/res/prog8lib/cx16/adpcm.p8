adpcm {

    ; IMA ADPCM decoder.  Supports mono and stereo streams.
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

    %option ignore_unused, private_symbols

    uword @requirezp predict       ; decoded 16 bit pcm sample for first channel.
    uword @requirezp predict_2     ; decoded 16 bit pcm sample for second channel.
    uword @requirezp rowptr        ; pointer to current adpcm_deltas row for first channel.
    uword @requirezp rowptr_2      ; pointer to current adpcm_deltas row for second channel.

    public sub decode_block_mono(uword @requirezp nibblesptr) {
        ; Decodes one 256 byte block of adpcm data, into the Vera's PCM FIFO buffer.
        ; Decoded data is 16 bit mono PCM, 505 samples = 1010 bytes.
        init(peekw(nibblesptr), @(nibblesptr+2))
        cx16.VERA_AUDIO_DATA = lsb(predict)
        cx16.VERA_AUDIO_DATA = msb(predict)
        nibblesptr += 4
        ubyte @zp nibble
        repeat 252/2 {
            unroll 2 {
                nibble = @(nibblesptr)
                ; note: when calling decode_nibble(), the upper nibble in the argument needs to be zero
                decode_nibble(nibble & 15)     ; first word
                cx16.VERA_AUDIO_DATA = lsb(predict)
                cx16.VERA_AUDIO_DATA = msb(predict)
                decode_nibble(nibble>>4)       ; second word
                cx16.VERA_AUDIO_DATA = lsb(predict)
                cx16.VERA_AUDIO_DATA = msb(predict)
                nibblesptr++
            }
        }
    }

    public sub decode_block_stereo(uword @requirezp nibblesptr) {
        ; Decodes one 256 byte block of adpcm data, into the Vera's PCM FIFO buffer.
        ; Decoded data is 16 bit stereo PCM, 498 samples = 996 bytes.
        init(peekw(nibblesptr), @(nibblesptr+2))            ; left channel
        cx16.VERA_AUDIO_DATA = lsb(predict)
        cx16.VERA_AUDIO_DATA = msb(predict)
        init_second(peekw(nibblesptr+4), @(nibblesptr+6))   ; right channel
        cx16.VERA_AUDIO_DATA = lsb(predict_2)
        cx16.VERA_AUDIO_DATA = msb(predict_2)
        nibblesptr += 8
        repeat 248/8
            decode_nibbles_unrolled()

        sub decode_nibbles_unrolled() {
            ; decode 4 left channel nibbles
            ; note: when calling decode_nibble(), the upper nibble in the argument needs to be zero
            uword[8] left
            uword[8] right
            ubyte @requirezp nibble = @(nibblesptr)
            decode_nibble(nibble & 15)     ; first word
            left[0] = predict
            decode_nibble(nibble>>4)       ; second word
            left[1] = predict
            nibble = @(nibblesptr+1)
            decode_nibble(nibble & 15)     ; first word
            left[2] = predict
            decode_nibble(nibble>>4)       ; second word
            left[3] = predict
            nibble = @(nibblesptr+2)
            decode_nibble(nibble & 15)     ; first word
            left[4] = predict
            decode_nibble(nibble>>4)       ; second word
            left[5] = predict
            nibble = @(nibblesptr+3)
            decode_nibble(nibble & 15)     ; first word
            left[6] = predict
            decode_nibble(nibble>>4)       ; second word
            left[7] = predict

            ; decode 4 right channel nibbles
            nibble = @(nibblesptr+4)
            decode_nibble_second(nibble & 15)     ; first word
            right[0] = predict_2
            decode_nibble_second(nibble>>4)       ; second word
            right[1] = predict_2
            nibble = @(nibblesptr+5)
            decode_nibble_second(nibble & 15)     ; first word
            right[2] = predict_2
            decode_nibble_second(nibble>>4)       ; second word
            right[3] = predict_2
            nibble = @(nibblesptr+6)
            decode_nibble_second(nibble & 15)     ; first word
            right[4] = predict_2
            decode_nibble_second(nibble>>4)       ; second word
            right[5] = predict_2
            nibble = @(nibblesptr+7)
            decode_nibble_second(nibble & 15)     ; first word
            right[6] = predict_2
            decode_nibble_second(nibble>>4)       ; second word
            right[7] = predict_2
            nibblesptr += 8

            %asm {{
                ; copy to vera PSG fifo buffer
                ldy  #0
    -           lda  p8v_left_lsb,y
                sta  cx16.VERA_AUDIO_DATA
                lda  p8v_left_msb,y
                sta  cx16.VERA_AUDIO_DATA
                lda  p8v_right_lsb,y
                sta  cx16.VERA_AUDIO_DATA
                lda  p8v_right_msb,y
                sta  cx16.VERA_AUDIO_DATA
                iny
                cpy  #8
                bne  -
            }}
        }
    }


    sub init(uword startPredict, ubyte startIndex) {
        ; initialize first decoding channel.
        predict = startPredict
        rowptr = &deltas_table + ((startIndex as uword) << 5)
    }

    sub init_second(uword startPredict_2, ubyte startIndex_2) {
        ; initialize second decoding channel.
        predict_2 = startPredict_2
        rowptr_2 = &deltas_table + ((startIndex_2 as uword) << 5)
    }

/*
    Prog8 equivalents of the optimized asm routines below:

    sub decode_nibble(ubyte nibble) {
        ; Decoder for a single nibble for the first channel. (value of 'nibble' needs to be strictly 0-15 !)
        ; This is the hotspot of the decoder algorithm.
        predict += peekw(rowptr + (nibble as uword * 2))
        rowptr = clamp(rowptr + t_rowdelta[nibble] as uword, &deltas_table, &deltas_table + 2816)
    }

    sub decode_nibble_second(ubyte nibble) {
        ; Decoder for a single nibble for the second channel. (value of 'nibble' needs to be strictly 0-15 !)
        ; This is the hotspot of the decoder algorithm.
        predict_2 += peekw(rowptr_2 + (nibble as uword * 2))
        rowptr_2 = clamp(rowptr_2 + t_rowdelta[nibble] as uword, &deltas_table, &deltas_table + 2816)
    }

    private word[] t_rowdelta = [-32,-32,-32,-32,64,128,192,256,-32,-32,-32,-32,64,128,192,256]

*/

    asmsub decode_nibble(ubyte nibble @A) clobbers(A, Y) {
        ; Decoder for a single nibble for the first channel. (value of 'nibble' needs to be strictly 0-15 !)
        ; This is the hotspot of the decoder algorithm.
        %asm {{
            sta  P8ZP_SCRATCH_B1            ; save nibble
            asl  a                          ; nibble*2; C=0 guaranteed since nibble<=15
            tay
            lda  (p8v_rowptr),y             ; delta LSB
            adc  p8v_predict                ; C=0 after asl
            sta  p8v_predict
            iny
            lda  (p8v_rowptr),y             ; delta MSB
            adc  p8v_predict+1
            sta  p8v_predict+1
            ldy  P8ZP_SCRATCH_B1            ; original nibble
            clc
            lda  p8v_rowptr
            adc  t_rowdelta_lsb,y           ; rowptr += signed word row delta
            sta  p8v_rowptr
            lda  p8v_rowptr+1
            adc  t_rowdelta_msb,y
            sta  p8v_rowptr+1
            ; clamp rowptr to [adpcm_deltas, adpcm_deltas+88*32]
            lda  p8v_rowptr+1
            cmp  #>adpcm_deltas
            bcc  _set_min
            bne  _check_max
            lda  p8v_rowptr
            cmp  #<adpcm_deltas
            bcc  _set_min
_check_max:
            lda  p8v_rowptr+1
            cmp  #(>adpcm_deltas)+11
            bcc  _done
            bne  _set_max
            lda  p8v_rowptr
            cmp  #<adpcm_deltas
            beq  _done
            bcc  _done
_set_max:
            lda  #<(adpcm_deltas+2816)
            sta  p8v_rowptr
            lda  #(>adpcm_deltas)+11
            sta  p8v_rowptr+1
            bne  _done
_set_min:
            lda  #<adpcm_deltas
            sta  p8v_rowptr
            lda  #>adpcm_deltas
            sta  p8v_rowptr+1
_done:
            rts
        }}
    }

    asmsub decode_nibble_second(ubyte nibble @A) clobbers(A, Y) {
        ; Decoder for a single nibble for the second channel. (value of 'nibble' needs to be strictly 0-15 !)
        ; This is the hotspot of the decoder algorithm.
        %asm {{
            sta  P8ZP_SCRATCH_B1            ; save nibble
            asl  a                          ; nibble*2; C=0 guaranteed since nibble<=15
            tay
            lda  (p8v_rowptr_2),y           ; delta LSB
            adc  p8v_predict_2              ; C=0 after asl
            sta  p8v_predict_2
            iny
            lda  (p8v_rowptr_2),y           ; delta MSB
            adc  p8v_predict_2+1
            sta  p8v_predict_2+1
            ldy  P8ZP_SCRATCH_B1            ; original nibble
            clc
            lda  p8v_rowptr_2
            adc  t_rowdelta_lsb,y           ; rowptr_2 += signed word row delta
            sta  p8v_rowptr_2
            lda  p8v_rowptr_2+1
            adc  t_rowdelta_msb,y
            sta  p8v_rowptr_2+1
            ; clamp rowptr_2 to [adpcm_deltas, adpcm_deltas+88*32]
            lda  p8v_rowptr_2+1
            cmp  #>adpcm_deltas
            bcc  _set_min
            bne  _check_max
            lda  p8v_rowptr_2
            cmp  #<adpcm_deltas
            bcc  _set_min
_check_max:
            lda  p8v_rowptr_2+1
            cmp  #(>adpcm_deltas)+11
            bcc  _done
            bne  _set_max
            lda  p8v_rowptr_2
            cmp  #<adpcm_deltas
            beq  _done
            bcc  _done
_set_max:
            lda  #<(adpcm_deltas+2816)
            sta  p8v_rowptr_2
            lda  #(>adpcm_deltas)+11
            sta  p8v_rowptr_2+1
            bne  _done
_set_min:
            lda  #<adpcm_deltas
            sta  p8v_rowptr_2
            lda  #>adpcm_deltas
            sta  p8v_rowptr_2+1
_done:
            rts
        }}
    }

deltas_table:
    %asm {{
        ; Precomputed IMA ADPCM delta table: 89 step sizes * 16 nibbles = 1424 signed 16-bit values.
        ; Delta = step/8 + (bit2? step) + (bit1? step/2) + (bit0? step/4), negated if nibble bit 3 set.
        ; Stored as interleaved signed 16-bit (.sint) words; index into it is (index<<4)|nibble.
        ; Note: deltas for large steps exceed +32767; they are stored two's-complement (e.g. +36862 as -28674).
        ; The 16-bit wrapping add in the decoder treats these the same as their unsigned representation.

adpcm_deltas:
        .sint  0, 1, 3, 4, 7, 8, 10, 11, 0, -1, -3, -4, -7, -8, -10, -11
        .sint  1, 3, 5, 7, 9, 11, 13, 15, -1, -3, -5, -7, -9, -11, -13, -15
        .sint  1, 3, 5, 7, 10, 12, 14, 16, -1, -3, -5, -7, -10, -12, -14, -16
        .sint  1, 3, 6, 8, 11, 13, 16, 18, -1, -3, -6, -8, -11, -13, -16, -18
        .sint  1, 3, 6, 8, 12, 14, 17, 19, -1, -3, -6, -8, -12, -14, -17, -19
        .sint  1, 4, 7, 10, 13, 16, 19, 22, -1, -4, -7, -10, -13, -16, -19, -22
        .sint  1, 4, 7, 10, 14, 17, 20, 23, -1, -4, -7, -10, -14, -17, -20, -23
        .sint  1, 4, 8, 11, 15, 18, 22, 25, -1, -4, -8, -11, -15, -18, -22, -25
        .sint  2, 6, 10, 14, 18, 22, 26, 30, -2, -6, -10, -14, -18, -22, -26, -30
        .sint  2, 6, 10, 14, 19, 23, 27, 31, -2, -6, -10, -14, -19, -23, -27, -31
        .sint  2, 6, 11, 15, 21, 25, 30, 34, -2, -6, -11, -15, -21, -25, -30, -34
        .sint  2, 7, 12, 17, 23, 28, 33, 38, -2, -7, -12, -17, -23, -28, -33, -38
        .sint  2, 7, 13, 18, 25, 30, 36, 41, -2, -7, -13, -18, -25, -30, -36, -41
        .sint  3, 9, 15, 21, 28, 34, 40, 46, -3, -9, -15, -21, -28, -34, -40, -46
        .sint  3, 10, 17, 24, 31, 38, 45, 52, -3, -10, -17, -24, -31, -38, -45, -52
        .sint  3, 10, 18, 25, 34, 41, 49, 56, -3, -10, -18, -25, -34, -41, -49, -56
        .sint  4, 12, 21, 29, 38, 46, 55, 63, -4, -12, -21, -29, -38, -46, -55, -63
        .sint  4, 13, 22, 31, 41, 50, 59, 68, -4, -13, -22, -31, -41, -50, -59, -68
        .sint  5, 15, 25, 35, 46, 56, 66, 76, -5, -15, -25, -35, -46, -56, -66, -76
        .sint  5, 16, 27, 38, 50, 61, 72, 83, -5, -16, -27, -38, -50, -61, -72, -83
        .sint  6, 18, 31, 43, 56, 68, 81, 93, -6, -18, -31, -43, -56, -68, -81, -93
        .sint  6, 19, 33, 46, 61, 74, 88, 101, -6, -19, -33, -46, -61, -74, -88, -101
        .sint  7, 22, 37, 52, 67, 82, 97, 112, -7, -22, -37, -52, -67, -82, -97, -112
        .sint  8, 24, 41, 57, 74, 90, 107, 123, -8, -24, -41, -57, -74, -90, -107, -123
        .sint  9, 27, 45, 63, 82, 100, 118, 136, -9, -27, -45, -63, -82, -100, -118, -136
        .sint  10, 30, 50, 70, 90, 110, 130, 150, -10, -30, -50, -70, -90, -110, -130, -150
        .sint  11, 33, 55, 77, 99, 121, 143, 165, -11, -33, -55, -77, -99, -121, -143, -165
        .sint  12, 36, 60, 84, 109, 133, 157, 181, -12, -36, -60, -84, -109, -133, -157, -181
        .sint  13, 39, 66, 92, 120, 146, 173, 199, -13, -39, -66, -92, -120, -146, -173, -199
        .sint  14, 43, 73, 102, 132, 161, 191, 220, -14, -43, -73, -102, -132, -161, -191, -220
        .sint  16, 48, 81, 113, 146, 178, 211, 243, -16, -48, -81, -113, -146, -178, -211, -243
        .sint  17, 52, 88, 123, 160, 195, 231, 266, -17, -52, -88, -123, -160, -195, -231, -266
        .sint  19, 58, 97, 136, 176, 215, 254, 293, -19, -58, -97, -136, -176, -215, -254, -293
        .sint  21, 64, 107, 150, 194, 237, 280, 323, -21, -64, -107, -150, -194, -237, -280, -323
        .sint  23, 70, 118, 165, 213, 260, 308, 355, -23, -70, -118, -165, -213, -260, -308, -355
        .sint  26, 78, 130, 182, 235, 287, 339, 391, -26, -78, -130, -182, -235, -287, -339, -391
        .sint  28, 85, 143, 200, 258, 315, 373, 430, -28, -85, -143, -200, -258, -315, -373, -430
        .sint  31, 94, 157, 220, 284, 347, 410, 473, -31, -94, -157, -220, -284, -347, -410, -473
        .sint  34, 103, 173, 242, 313, 382, 452, 521, -34, -103, -173, -242, -313, -382, -452, -521
        .sint  38, 114, 191, 267, 345, 421, 498, 574, -38, -114, -191, -267, -345, -421, -498, -574
        .sint  42, 126, 210, 294, 379, 463, 547, 631, -42, -126, -210, -294, -379, -463, -547, -631
        .sint  46, 138, 231, 323, 417, 509, 602, 694, -46, -138, -231, -323, -417, -509, -602, -694
        .sint  51, 153, 255, 357, 459, 561, 663, 765, -51, -153, -255, -357, -459, -561, -663, -765
        .sint  56, 168, 280, 392, 505, 617, 729, 841, -56, -168, -280, -392, -505, -617, -729, -841
        .sint  61, 184, 308, 431, 555, 678, 802, 925, -61, -184, -308, -431, -555, -678, -802, -925
        .sint  68, 204, 340, 476, 612, 748, 884, 1020, -68, -204, -340, -476, -612, -748, -884, -1020
        .sint  74, 223, 373, 522, 672, 821, 971, 1120, -74, -223, -373, -522, -672, -821, -971, -1120
        .sint  82, 246, 411, 575, 740, 904, 1069, 1233, -82, -246, -411, -575, -740, -904, -1069, -1233
        .sint  90, 271, 452, 633, 814, 995, 1176, 1357, -90, -271, -452, -633, -814, -995, -1176, -1357
        .sint  99, 298, 497, 696, 895, 1094, 1293, 1492, -99, -298, -497, -696, -895, -1094, -1293, -1492
        .sint  109, 328, 547, 766, 985, 1204, 1423, 1642, -109, -328, -547, -766, -985, -1204, -1423, -1642
        .sint  120, 360, 601, 841, 1083, 1323, 1564, 1804, -120, -360, -601, -841, -1083, -1323, -1564, -1804
        .sint  132, 397, 662, 927, 1192, 1457, 1722, 1987, -132, -397, -662, -927, -1192, -1457, -1722, -1987
        .sint  145, 436, 728, 1019, 1311, 1602, 1894, 2185, -145, -436, -728, -1019, -1311, -1602, -1894, -2185
        .sint  160, 480, 801, 1121, 1442, 1762, 2083, 2403, -160, -480, -801, -1121, -1442, -1762, -2083, -2403
        .sint  176, 528, 881, 1233, 1587, 1939, 2292, 2644, -176, -528, -881, -1233, -1587, -1939, -2292, -2644
        .sint  194, 582, 970, 1358, 1746, 2134, 2522, 2910, -194, -582, -970, -1358, -1746, -2134, -2522, -2910
        .sint  213, 639, 1066, 1492, 1920, 2346, 2773, 3199, -213, -639, -1066, -1492, -1920, -2346, -2773, -3199
        .sint  234, 703, 1173, 1642, 2112, 2581, 3051, 3520, -234, -703, -1173, -1642, -2112, -2581, -3051, -3520
        .sint  258, 774, 1291, 1807, 2324, 2840, 3357, 3873, -258, -774, -1291, -1807, -2324, -2840, -3357, -3873
        .sint  284, 852, 1420, 1988, 2556, 3124, 3692, 4260, -284, -852, -1420, -1988, -2556, -3124, -3692, -4260
        .sint  312, 936, 1561, 2185, 2811, 3435, 4060, 4684, -312, -936, -1561, -2185, -2811, -3435, -4060, -4684
        .sint  343, 1030, 1717, 2404, 3092, 3779, 4466, 5153, -343, -1030, -1717, -2404, -3092, -3779, -4466, -5153
        .sint  378, 1134, 1890, 2646, 3402, 4158, 4914, 5670, -378, -1134, -1890, -2646, -3402, -4158, -4914, -5670
        .sint  415, 1246, 2078, 2909, 3742, 4573, 5405, 6236, -415, -1246, -2078, -2909, -3742, -4573, -5405, -6236
        .sint  457, 1372, 2287, 3202, 4117, 5032, 5947, 6862, -457, -1372, -2287, -3202, -4117, -5032, -5947, -6862
        .sint  503, 1509, 2516, 3522, 4529, 5535, 6542, 7548, -503, -1509, -2516, -3522, -4529, -5535, -6542, -7548
        .sint  553, 1660, 2767, 3874, 4981, 6088, 7195, 8302, -553, -1660, -2767, -3874, -4981, -6088, -7195, -8302
        .sint  608, 1825, 3043, 4260, 5479, 6696, 7914, 9131, -608, -1825, -3043, -4260, -5479, -6696, -7914, -9131
        .sint  669, 2008, 3348, 4687, 6027, 7366, 8706, 10045, -669, -2008, -3348, -4687, -6027, -7366, -8706, -10045
        .sint  736, 2209, 3683, 5156, 6630, 8103, 9577, 11050, -736, -2209, -3683, -5156, -6630, -8103, -9577, -11050
        .sint  810, 2431, 4052, 5673, 7294, 8915, 10536, 12157, -810, -2431, -4052, -5673, -7294, -8915, -10536, -12157
        .sint  891, 2674, 4457, 6240, 8023, 9806, 11589, 13372, -891, -2674, -4457, -6240, -8023, -9806, -11589, -13372
        .sint  980, 2941, 4902, 6863, 8825, 10786, 12747, 14708, -980, -2941, -4902, -6863, -8825, -10786, -12747, -14708
        .sint  1078, 3235, 5393, 7550, 9708, 11865, 14023, 16180, -1078, -3235, -5393, -7550, -9708, -11865, -14023, -16180
        .sint  1186, 3559, 5932, 8305, 10679, 13052, 15425, 17798, -1186, -3559, -5932, -8305, -10679, -13052, -15425, -17798
        .sint  1305, 3915, 6526, 9136, 11747, 14357, 16968, 19578, -1305, -3915, -6526, -9136, -11747, -14357, -16968, -19578
        .sint  1435, 4306, 7178, 10049, 12922, 15793, 18665, 21536, -1435, -4306, -7178, -10049, -12922, -15793, -18665, -21536
        .sint  1579, 4737, 7896, 11054, 14214, 17372, 20531, 23689, -1579, -4737, -7896, -11054, -14214, -17372, -20531, -23689
        .sint  1737, 5211, 8686, 12160, 15636, 19110, 22585, 26059, -1737, -5211, -8686, -12160, -15636, -19110, -22585, -26059
        .sint  1911, 5733, 9555, 13377, 17200, 21022, 24844, 28666, -1911, -5733, -9555, -13377, -17200, -21022, -24844, -28666
        .sint  2102, 6306, 10511, 14715, 18920, 23124, 27329, 31533, -2102, -6306, -10511, -14715, -18920, -23124, -27329, -31533
        .sint  2312, 6937, 11562, 16187, 20812, 25437, 30062, -30849, -2312, -6937, -11562, -16187, -20812, -25437, -30062, 30849
        .sint  2543, 7630, 12718, 17805, 22893, 27980, -32468, -27381, -2543, -7630, -12718, -17805, -22893, -27980, 32468, 27381
        .sint  2798, 8394, 13990, 19586, 25183, 30779, -29161, -23565, -2798, -8394, -13990, -19586, -25183, -30779, 29161, 23565
        .sint  3077, 9232, 15388, 21543, 27700, -31681, -25525, -19370, -3077, -9232, -15388, -21543, -27700, 31681, 25525, 19370
        .sint  3385, 10156, 16928, 23699, 30471, -28294, -21522, -14751, -3385, -10156, -16928, -23699, -30471, 28294, 21522, 14751
        .sint  3724, 11172, 18621, 26069, -32018, -24570, -17121, -9673, -3724, -11172, -18621, -26069, 32018, 24570, 17121, 9673
        .sint  4095, 12286, 20478, 28669, -28674, -20483, -12291, -4100, -4095, -12286, -20478, -28669, 28674, 20483, 12291, 4100
; Row pointer deltas (t_index * 32) as signed 16-bit values, split into LSB/MSB tables.
t_rowdelta_lsb:
        .byte  224, 224, 224, 224, 64, 128, 192, 0, 224, 224, 224, 224, 64, 128, 192, 0
t_rowdelta_msb:
        .byte  255, 255, 255, 255, 0, 0, 0, 1, 255, 255, 255, 255, 0, 0, 0, 1
    }}
}
