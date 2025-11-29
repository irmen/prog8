adpcm {

    sub decode_benchmark(uword max_time) -> uword {
        uword num_blocks
        txt.nl()
        cbm.SETTIM(0,0,0)

        while cbm.RDTIM16()<max_time {
            adpcm.init(0,0)
            uword @requirezp nibbles_ptr = $0800 ; for benchmark purposes, the exact nibbles don't really matter, so we just take this benchmark program itself as input
            repeat 252/2 {
                unroll 2 {
                    ubyte @zp nibble = @(nibbles_ptr)
                    adpcm.decode_nibble(nibble & 15)     ; first word  (note: upper nibble needs to be zero!)
                    adpcm.decode_nibble(nibble>>4)       ; second word  (note: upper nibble is zero, after the shifts.)
                    nibbles_ptr++
                }
            }
            num_blocks++
            txt.chrout('.')
        }

        return num_blocks
    }

    ; IMA ADPCM decoder.  Supports mono and stereo streams.

    byte[] t_index = [ -1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]
    uword[] t_step = [
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
    ubyte @requirezp index
    uword @requirezp pstep

    sub init(uword startPredict, ubyte startIndex) {
        ; initialize first decoding channel.
        predict = startPredict
        index = startIndex
        pstep = t_step[index]
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
}
