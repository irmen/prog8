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


    byte[] t_index = [ -1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]
    uword[] @split t_step = [
            7, 8, 9, 10, 11, 12, 13, 14,
            16, 17, 19, 21, 23, 25, 28, 31,
            34, 37, 41, 45, 50, 55, 60, 66,
            73, 80, 88, 97, 107, 118, 130, 143,
            157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658,
            724, 796, 876, 963, 1060, 1166, 1282, 1411,
            1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024,
            3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484,
            7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
            15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
            32767]

    uword @requirezp predict       ; decoded 16 bit pcm sample for first channel.
    uword @requirezp predict_2     ; decoded 16 bit pcm sample for second channel.
    ubyte @requirezp index
    ubyte @requirezp index_2
    uword @requirezp pstep
    uword @requirezp pstep_2

    sub init(uword startPredict, ubyte startIndex) {
        ; initialize first decoding channel.
        predict = startPredict
        index = startIndex
        pstep = t_step[index]
    }

    sub init_second(uword startPredict_2, ubyte startIndex_2) {
        ; initialize second decoding channel.
        predict_2 = startPredict_2
        index_2 = startIndex_2
        pstep_2 = t_step[index_2]
    }

    sub decode_nibble(ubyte @zp nibble) {
        ; Decoder for a single nibble for the first channel. (value of 'nibble' needs to be strictly 0-15 !)
        ; This is the hotspot of the decoder algorithm!
        ; Note that the generated assembly from this is pretty efficient,
        ; rewriting it by hand in asm seems to improve it only ~10%.
        cx16.r0s = 0                ; difference
        if nibble & %0100 !=0
            cx16.r0s += pstep
        pstep >>= 1
        if nibble & %0010 !=0
            cx16.r0s += pstep
        pstep >>= 1
        if nibble & %0001 !=0
            cx16.r0s += pstep
        pstep >>= 1
        cx16.r0s += pstep
        if nibble & %1000 !=0
            predict -= cx16.r0
        else
            predict += cx16.r0

        ; NOTE: the original C/Python code uses a 32 bits prediction value and clips it to a 16 bit word
        ;       but for speed reasons we only work with 16 bit words here all the time (with possible clipping error)
        ; if predicted > 32767:
        ;    predicted = 32767
        ; elif predicted < -32767:
        ;    predicted = - 32767

        index += t_index[nibble] as ubyte
        if_neg
            index = 0
        else if index >= len(t_step)-1
            index = len(t_step)-1
        pstep = t_step[index]
    }

    sub decode_nibble_second(ubyte @zp nibble) {
        ; Decoder for nibbles for the second channel.
        ; this is the hotspot of the decoder algorithm!
        ; Note that the generated assembly from this is pretty efficient,
        ; rewriting it by hand in asm seems to improve it only 5-10%
        cx16.r0s = 0                ; difference
        if nibble & %0100 !=0
            cx16.r0s += pstep_2
        pstep_2 >>= 1
        if nibble & %0010 !=0
            cx16.r0s += pstep_2
        pstep_2 >>= 1
        if nibble & %0001 !=0
            cx16.r0s += pstep_2
        pstep_2 >>= 1
        cx16.r0s += pstep_2
        if nibble & %1000 !=0
            predict_2 -= cx16.r0
        else
            predict_2 += cx16.r0

        ; NOTE: the original C/Python code uses a 32 bits prediction value and clips it to a 16 bit word
        ;       but for speed reasons we only work with 16 bit words here all the time (with possible clipping error)
        ; if predicted > 32767:
        ;    predicted = 32767
        ; elif predicted < -32767:
        ;    predicted = - 32767

        index_2 += t_index[nibble] as ubyte
        if_neg
            index_2 = 0
        else if index_2 >= len(t_step)-1
            index_2 = len(t_step)-1
        pstep_2 = t_step[index_2]
    }
}
