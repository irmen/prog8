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

    byte[] t_index = [ -1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]

    uword @requirezp predict       ; decoded 16 bit pcm sample for first channel.
    uword @requirezp predict_2     ; decoded 16 bit pcm sample for second channel.
    ubyte @requirezp index
    ubyte @requirezp index_2

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
        index = startIndex
    }

    sub init_second(uword startPredict_2, ubyte startIndex_2) {
        ; initialize second decoding channel.
        predict_2 = startPredict_2
        index_2 = startIndex_2
    }

/*
    Prog8 equivalents of the optimized asm routines below:

    sub decode_nibble(ubyte nibble) {
        predict += peekw(&deltas_table + ((index as uword << 4) | nibble) * 2)
        index += t_index[nibble] as ubyte
        if_neg
            index = 0
        else if index >= 88
            index = 88
    }

    sub decode_nibble_second(ubyte nibble) {
        predict_2 += peekw(&deltas_table + ((index_2 as uword << 4) | nibble) * 2)
        index_2 += t_index[nibble] as ubyte
        if_neg
            index_2 = 0
        else if index_2 >= 88
            index_2 = 88
    }
*/

    asmsub decode_nibble(ubyte nibble @A) clobbers(A, Y) {
        ; Decoder for a single nibble for the first channel. (value of 'nibble' needs to be strictly 0-15 !)
        ; This is the hotspot of the decoder algorithm.
        %asm {{
            sta  P8ZP_SCRATCH_B1            ; save nibble
            lda  p8v_index
            lsr  a
            lsr  a
            lsr  a
            lsr  a
            sta  P8ZP_SCRATCH_W1+1          ; hi byte of index*16
            lda  p8v_index
            asl  a
            asl  a
            asl  a
            asl  a
            ora  P8ZP_SCRATCH_B1            ; lo byte = (index<<4)|nibble
            asl  a                          ; *2 for word offset
            rol  P8ZP_SCRATCH_W1+1
            clc
            adc  #<adpcm_deltas
            sta  P8ZP_SCRATCH_W1
            lda  P8ZP_SCRATCH_W1+1
            adc  #>adpcm_deltas
            sta  P8ZP_SCRATCH_W1+1          ; pointer to delta word
            ldy  #0
            lda  (P8ZP_SCRATCH_W1),y        ; delta LSB
            clc
            adc  p8v_predict
            sta  p8v_predict
            iny
            lda  (P8ZP_SCRATCH_W1),y        ; delta MSB
            adc  p8v_predict+1
            sta  p8v_predict+1
            ldy  P8ZP_SCRATCH_B1
            lda  p8v_index
            clc
            adc  p8v_t_index,y              ; signed byte add, result wraps mod 256
            bpl  _nonneg
            lda  #0
            beq  _done
_nonneg     cmp  #89
            bcc  _done
            lda  #88
_done       sta  p8v_index
            rts
        }}
    }

    asmsub decode_nibble_second(ubyte nibble @A) clobbers(A, Y) {
        ; Decoder for a single nibble for the second channel. (value of 'nibble' needs to be strictly 0-15 !)
        ; This is the hotspot of the decoder algorithm.
        %asm {{
            sta  P8ZP_SCRATCH_B1            ; save nibble
            lda  p8v_index_2
            lsr  a
            lsr  a
            lsr  a
            lsr  a
            sta  P8ZP_SCRATCH_W1+1          ; hi byte of index*16
            lda  p8v_index_2
            asl  a
            asl  a
            asl  a
            asl  a
            ora  P8ZP_SCRATCH_B1            ; lo byte = (index<<4)|nibble
            asl  a                          ; *2 for word offset
            rol  P8ZP_SCRATCH_W1+1
            clc
            adc  #<adpcm_deltas
            sta  P8ZP_SCRATCH_W1
            lda  P8ZP_SCRATCH_W1+1
            adc  #>adpcm_deltas
            sta  P8ZP_SCRATCH_W1+1          ; pointer to delta word
            ldy  #0
            lda  (P8ZP_SCRATCH_W1),y        ; delta LSB
            clc
            adc  p8v_predict_2
            sta  p8v_predict_2
            iny
            lda  (P8ZP_SCRATCH_W1),y        ; delta MSB
            adc  p8v_predict_2+1
            sta  p8v_predict_2+1
            ldy  P8ZP_SCRATCH_B1
            lda  p8v_index_2
            clc
            adc  p8v_t_index,y              ; signed byte add, result wraps mod 256
            bpl  _nonneg
            lda  #0
            beq  _done
_nonneg     cmp  #89
            bcc  _done
            lda  #88
_done       sta  p8v_index_2
            rts
        }}
    }

    %asm {{
        ; Precomputed IMA ADPCM delta table: 89 step sizes * 16 nibbles = 1424 signed 16-bit values.
        ; Delta = step/8 + (bit2? step) + (bit1? step/2) + (bit0? step/4), negated if nibble bit 3 set.
        ; Stored as interleaved little-endian 16-bit words; index into it is (index<<4)|nibble.

adpcm_deltas:
        .word  0, 1, 3, 4, 7, 8, 10, 11
        .word  0, 65535, 65533, 65532, 65529, 65528, 65526, 65525
        .word  1, 3, 5, 7, 9, 11, 13, 15
        .word  65535, 65533, 65531, 65529, 65527, 65525, 65523, 65521
        .word  1, 3, 5, 7, 10, 12, 14, 16
        .word  65535, 65533, 65531, 65529, 65526, 65524, 65522, 65520
        .word  1, 3, 6, 8, 11, 13, 16, 18
        .word  65535, 65533, 65530, 65528, 65525, 65523, 65520, 65518
        .word  1, 3, 6, 8, 12, 14, 17, 19
        .word  65535, 65533, 65530, 65528, 65524, 65522, 65519, 65517
        .word  1, 4, 7, 10, 13, 16, 19, 22
        .word  65535, 65532, 65529, 65526, 65523, 65520, 65517, 65514
        .word  1, 4, 7, 10, 14, 17, 20, 23
        .word  65535, 65532, 65529, 65526, 65522, 65519, 65516, 65513
        .word  1, 4, 8, 11, 15, 18, 22, 25
        .word  65535, 65532, 65528, 65525, 65521, 65518, 65514, 65511
        .word  2, 6, 10, 14, 18, 22, 26, 30
        .word  65534, 65530, 65526, 65522, 65518, 65514, 65510, 65506
        .word  2, 6, 10, 14, 19, 23, 27, 31
        .word  65534, 65530, 65526, 65522, 65517, 65513, 65509, 65505
        .word  2, 6, 11, 15, 21, 25, 30, 34
        .word  65534, 65530, 65525, 65521, 65515, 65511, 65506, 65502
        .word  2, 7, 12, 17, 23, 28, 33, 38
        .word  65534, 65529, 65524, 65519, 65513, 65508, 65503, 65498
        .word  2, 7, 13, 18, 25, 30, 36, 41
        .word  65534, 65529, 65523, 65518, 65511, 65506, 65500, 65495
        .word  3, 9, 15, 21, 28, 34, 40, 46
        .word  65533, 65527, 65521, 65515, 65508, 65502, 65496, 65490
        .word  3, 10, 17, 24, 31, 38, 45, 52
        .word  65533, 65526, 65519, 65512, 65505, 65498, 65491, 65484
        .word  3, 10, 18, 25, 34, 41, 49, 56
        .word  65533, 65526, 65518, 65511, 65502, 65495, 65487, 65480
        .word  4, 12, 21, 29, 38, 46, 55, 63
        .word  65532, 65524, 65515, 65507, 65498, 65490, 65481, 65473
        .word  4, 13, 22, 31, 41, 50, 59, 68
        .word  65532, 65523, 65514, 65505, 65495, 65486, 65477, 65468
        .word  5, 15, 25, 35, 46, 56, 66, 76
        .word  65531, 65521, 65511, 65501, 65490, 65480, 65470, 65460
        .word  5, 16, 27, 38, 50, 61, 72, 83
        .word  65531, 65520, 65509, 65498, 65486, 65475, 65464, 65453
        .word  6, 18, 31, 43, 56, 68, 81, 93
        .word  65530, 65518, 65505, 65493, 65480, 65468, 65455, 65443
        .word  6, 19, 33, 46, 61, 74, 88, 101
        .word  65530, 65517, 65503, 65490, 65475, 65462, 65448, 65435
        .word  7, 22, 37, 52, 67, 82, 97, 112
        .word  65529, 65514, 65499, 65484, 65469, 65454, 65439, 65424
        .word  8, 24, 41, 57, 74, 90, 107, 123
        .word  65528, 65512, 65495, 65479, 65462, 65446, 65429, 65413
        .word  9, 27, 45, 63, 82, 100, 118, 136
        .word  65527, 65509, 65491, 65473, 65454, 65436, 65418, 65400
        .word  10, 30, 50, 70, 90, 110, 130, 150
        .word  65526, 65506, 65486, 65466, 65446, 65426, 65406, 65386
        .word  11, 33, 55, 77, 99, 121, 143, 165
        .word  65525, 65503, 65481, 65459, 65437, 65415, 65393, 65371
        .word  12, 36, 60, 84, 109, 133, 157, 181
        .word  65524, 65500, 65476, 65452, 65427, 65403, 65379, 65355
        .word  13, 39, 66, 92, 120, 146, 173, 199
        .word  65523, 65497, 65470, 65444, 65416, 65390, 65363, 65337
        .word  14, 43, 73, 102, 132, 161, 191, 220
        .word  65522, 65493, 65463, 65434, 65404, 65375, 65345, 65316
        .word  16, 48, 81, 113, 146, 178, 211, 243
        .word  65520, 65488, 65455, 65423, 65390, 65358, 65325, 65293
        .word  17, 52, 88, 123, 160, 195, 231, 266
        .word  65519, 65484, 65448, 65413, 65376, 65341, 65305, 65270
        .word  19, 58, 97, 136, 176, 215, 254, 293
        .word  65517, 65478, 65439, 65400, 65360, 65321, 65282, 65243
        .word  21, 64, 107, 150, 194, 237, 280, 323
        .word  65515, 65472, 65429, 65386, 65342, 65299, 65256, 65213
        .word  23, 70, 118, 165, 213, 260, 308, 355
        .word  65513, 65466, 65418, 65371, 65323, 65276, 65228, 65181
        .word  26, 78, 130, 182, 235, 287, 339, 391
        .word  65510, 65458, 65406, 65354, 65301, 65249, 65197, 65145
        .word  28, 85, 143, 200, 258, 315, 373, 430
        .word  65508, 65451, 65393, 65336, 65278, 65221, 65163, 65106
        .word  31, 94, 157, 220, 284, 347, 410, 473
        .word  65505, 65442, 65379, 65316, 65252, 65189, 65126, 65063
        .word  34, 103, 173, 242, 313, 382, 452, 521
        .word  65502, 65433, 65363, 65294, 65223, 65154, 65084, 65015
        .word  38, 114, 191, 267, 345, 421, 498, 574
        .word  65498, 65422, 65345, 65269, 65191, 65115, 65038, 64962
        .word  42, 126, 210, 294, 379, 463, 547, 631
        .word  65494, 65410, 65326, 65242, 65157, 65073, 64989, 64905
        .word  46, 138, 231, 323, 417, 509, 602, 694
        .word  65490, 65398, 65305, 65213, 65119, 65027, 64934, 64842
        .word  51, 153, 255, 357, 459, 561, 663, 765
        .word  65485, 65383, 65281, 65179, 65077, 64975, 64873, 64771
        .word  56, 168, 280, 392, 505, 617, 729, 841
        .word  65480, 65368, 65256, 65144, 65031, 64919, 64807, 64695
        .word  61, 184, 308, 431, 555, 678, 802, 925
        .word  65475, 65352, 65228, 65105, 64981, 64858, 64734, 64611
        .word  68, 204, 340, 476, 612, 748, 884, 1020
        .word  65468, 65332, 65196, 65060, 64924, 64788, 64652, 64516
        .word  74, 223, 373, 522, 672, 821, 971, 1120
        .word  65462, 65313, 65163, 65014, 64864, 64715, 64565, 64416
        .word  82, 246, 411, 575, 740, 904, 1069, 1233
        .word  65454, 65290, 65125, 64961, 64796, 64632, 64467, 64303
        .word  90, 271, 452, 633, 814, 995, 1176, 1357
        .word  65446, 65265, 65084, 64903, 64722, 64541, 64360, 64179
        .word  99, 298, 497, 696, 895, 1094, 1293, 1492
        .word  65437, 65238, 65039, 64840, 64641, 64442, 64243, 64044
        .word  109, 328, 547, 766, 985, 1204, 1423, 1642
        .word  65427, 65208, 64989, 64770, 64551, 64332, 64113, 63894
        .word  120, 360, 601, 841, 1083, 1323, 1564, 1804
        .word  65416, 65176, 64935, 64695, 64453, 64213, 63972, 63732
        .word  132, 397, 662, 927, 1192, 1457, 1722, 1987
        .word  65404, 65139, 64874, 64609, 64344, 64079, 63814, 63549
        .word  145, 436, 728, 1019, 1311, 1602, 1894, 2185
        .word  65391, 65100, 64808, 64517, 64225, 63934, 63642, 63351
        .word  160, 480, 801, 1121, 1442, 1762, 2083, 2403
        .word  65376, 65056, 64735, 64415, 64094, 63774, 63453, 63133
        .word  176, 528, 881, 1233, 1587, 1939, 2292, 2644
        .word  65360, 65008, 64655, 64303, 63949, 63597, 63244, 62892
        .word  194, 582, 970, 1358, 1746, 2134, 2522, 2910
        .word  65342, 64954, 64566, 64178, 63790, 63402, 63014, 62626
        .word  213, 639, 1066, 1492, 1920, 2346, 2773, 3199
        .word  65323, 64897, 64470, 64044, 63616, 63190, 62763, 62337
        .word  234, 703, 1173, 1642, 2112, 2581, 3051, 3520
        .word  65302, 64833, 64363, 63894, 63424, 62955, 62485, 62016
        .word  258, 774, 1291, 1807, 2324, 2840, 3357, 3873
        .word  65278, 64762, 64245, 63729, 63212, 62696, 62179, 61663
        .word  284, 852, 1420, 1988, 2556, 3124, 3692, 4260
        .word  65252, 64684, 64116, 63548, 62980, 62412, 61844, 61276
        .word  312, 936, 1561, 2185, 2811, 3435, 4060, 4684
        .word  65224, 64600, 63975, 63351, 62725, 62101, 61476, 60852
        .word  343, 1030, 1717, 2404, 3092, 3779, 4466, 5153
        .word  65193, 64506, 63819, 63132, 62444, 61757, 61070, 60383
        .word  378, 1134, 1890, 2646, 3402, 4158, 4914, 5670
        .word  65158, 64402, 63646, 62890, 62134, 61378, 60622, 59866
        .word  415, 1246, 2078, 2909, 3742, 4573, 5405, 6236
        .word  65121, 64290, 63458, 62627, 61794, 60963, 60131, 59300
        .word  457, 1372, 2287, 3202, 4117, 5032, 5947, 6862
        .word  65079, 64164, 63249, 62334, 61419, 60504, 59589, 58674
        .word  503, 1509, 2516, 3522, 4529, 5535, 6542, 7548
        .word  65033, 64027, 63020, 62014, 61007, 60001, 58994, 57988
        .word  553, 1660, 2767, 3874, 4981, 6088, 7195, 8302
        .word  64983, 63876, 62769, 61662, 60555, 59448, 58341, 57234
        .word  608, 1825, 3043, 4260, 5479, 6696, 7914, 9131
        .word  64928, 63711, 62493, 61276, 60057, 58840, 57622, 56405
        .word  669, 2008, 3348, 4687, 6027, 7366, 8706, 10045
        .word  64867, 63528, 62188, 60849, 59509, 58170, 56830, 55491
        .word  736, 2209, 3683, 5156, 6630, 8103, 9577, 11050
        .word  64800, 63327, 61853, 60380, 58906, 57433, 55959, 54486
        .word  810, 2431, 4052, 5673, 7294, 8915, 10536, 12157
        .word  64726, 63105, 61484, 59863, 58242, 56621, 55000, 53379
        .word  891, 2674, 4457, 6240, 8023, 9806, 11589, 13372
        .word  64645, 62862, 61079, 59296, 57513, 55730, 53947, 52164
        .word  980, 2941, 4902, 6863, 8825, 10786, 12747, 14708
        .word  64556, 62595, 60634, 58673, 56711, 54750, 52789, 50828
        .word  1078, 3235, 5393, 7550, 9708, 11865, 14023, 16180
        .word  64458, 62301, 60143, 57986, 55828, 53671, 51513, 49356
        .word  1186, 3559, 5932, 8305, 10679, 13052, 15425, 17798
        .word  64350, 61977, 59604, 57231, 54857, 52484, 50111, 47738
        .word  1305, 3915, 6526, 9136, 11747, 14357, 16968, 19578
        .word  64231, 61621, 59010, 56400, 53789, 51179, 48568, 45958
        .word  1435, 4306, 7178, 10049, 12922, 15793, 18665, 21536
        .word  64101, 61230, 58358, 55487, 52614, 49743, 46871, 44000
        .word  1579, 4737, 7896, 11054, 14214, 17372, 20531, 23689
        .word  63957, 60799, 57640, 54482, 51322, 48164, 45005, 41847
        .word  1737, 5211, 8686, 12160, 15636, 19110, 22585, 26059
        .word  63799, 60325, 56850, 53376, 49900, 46426, 42951, 39477
        .word  1911, 5733, 9555, 13377, 17200, 21022, 24844, 28666
        .word  63625, 59803, 55981, 52159, 48336, 44514, 40692, 36870
        .word  2102, 6306, 10511, 14715, 18920, 23124, 27329, 31533
        .word  63434, 59230, 55025, 50821, 46616, 42412, 38207, 34003
        .word  2312, 6937, 11562, 16187, 20812, 25437, 30062, 34687
        .word  63224, 58599, 53974, 49349, 44724, 40099, 35474, 30849
        .word  2543, 7630, 12718, 17805, 22893, 27980, 33068, 38155
        .word  62993, 57906, 52818, 47731, 42643, 37556, 32468, 27381
        .word  2798, 8394, 13990, 19586, 25183, 30779, 36375, 41971
        .word  62738, 57142, 51546, 45950, 40353, 34757, 29161, 23565
        .word  3077, 9232, 15388, 21543, 27700, 33855, 40011, 46166
        .word  62459, 56304, 50148, 43993, 37836, 31681, 25525, 19370
        .word  3385, 10156, 16928, 23699, 30471, 37242, 44014, 50785
        .word  62151, 55380, 48608, 41837, 35065, 28294, 21522, 14751
        .word  3724, 11172, 18621, 26069, 33518, 40966, 48415, 55863
        .word  61812, 54364, 46915, 39467, 32018, 24570, 17121, 9673
        .word  4095, 12286, 20478, 28669, 36862, 45053, 53245, 61436
        .word  61441, 53250, 45058, 36867, 28674, 20483, 12291, 4100
    }}
}
