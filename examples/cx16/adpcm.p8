%import textio

main {

    sub start() {

        ; cx16.set_rasterirq(irqhandler, 10)
        uword adpcm_size = &adpcm_data_end - &adpcm_data
        uword num_adpcm_blocks = adpcm_size / 256
        uword nibblesptr = &adpcm_data

        num_adpcm_blocks = 1
        nibblesptr = &nibbles

        repeat num_adpcm_blocks {
            uword @zp sample = peekw(nibblesptr)
            nibblesptr += 2
            adpcm.init(sample, @(nibblesptr))
            nibblesptr += 2

            repeat 252 {
               ubyte @zp nibble = @(nibblesptr)
               adpcm.decode_nibble(nibble & 15)
               txt.print_w(adpcm.predict as word)
               txt.spc()
               adpcm.decode_nibble(nibble>>4)
               txt.print_w(adpcm.predict as word)
               txt.spc()
               nibblesptr++
            }
        }

        repeat {
        }
    }

;    sub irqhandler() {
;        cx16.vpoke(1, $fa00, 255)
;
;        uword @zp sample = peekw(nibbles)
;        adpcm.init(sample, nibbles[2])
;
;        ubyte @zp idx = 4
;        while idx<100 {
;            ubyte @zp nibble = nibbles[idx]
;            adpcm.decode_nibble(nibble & 15)
;            sample = adpcm.predict
;            nibble >>= 4
;            adpcm.decode_nibble(nibble)
;            sample = adpcm.predict
;            idx++
;        }
;
;        cx16.vpoke(1, $fa00,0)
;    }

adpcm_data:
    %asmbinary "adpcm-mono.bin"
adpcm_data_end:

    ubyte[256] nibbles = [216, 227, 59, 0, 32, 2, 186, 139, 24, 34, 84, 115, 19, 185, 173, 8, 0, 56, 162,
                          169, 144, 113, 55, 130, 186, 12, 33, 144, 10, 136, 10, 129, 41, 66, 210, 207,
                          9, 0, 153, 129, 185, 173, 155, 0, 37, 162, 191, 27, 36, 129, 144, 137, 82, 18,
                          87, 18, 152, 153, 136, 0, 32, 23, 0, 40, 24, 117, 19, 202, 138, 8, 176, 171,
                          24, 131, 234, 9, 32, 39, 160, 10, 153, 33, 52, 209, 137, 200, 73, 33, 233, 155,
                          136, 66, 146, 8, 152, 56, 131, 113, 23, 168, 169, 185, 9, 160, 65, 37, 49, 22,
                          64, 68, 168, 138, 153, 1, 171, 88, 35, 25, 65, 1, 176, 173, 185, 175, 168, 154,
                          153, 205, 138, 144, 152, 154, 204, 43, 53, 51, 35, 154, 184, 96, 71, 34, 152,
                          172, 138, 160, 9, 24, 16, 204, 88, 48, 54, 160, 172, 152, 65, 132, 168, 48, 4,
                          0, 190, 9, 161, 155, 218, 139, 154, 185, 207, 9, 50, 130, 184, 252, 155, 24,
                          53, 144, 172, 170, 88, 51, 1, 176, 173, 17, 40, 115, 129, 136, 200, 11, 70,
                          129, 8, 0, 0, 48, 37, 160, 42, 35, 32, 17, 18, 220, 174, 170, 143, 9, 137, 160,
                          171, 104, 4, 169, 154, 219, 142, 65, 129, 128, 168, 9, 69, 50, 130, 219, 128,
                          138, 49, 52, 146, 237, 141, 50, 21, 8, 136, 8, 153, 35, 193, 170, 2, 169, 224,
                          186, 170, 40, 253, 12, 17, 25]

}


adpcm {
    ubyte[] t_index = [ -1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]
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

    uword @zp predict
    ubyte @zp index
    uword @zp pstep

    sub init(uword startPredict, ubyte startIndex) {
        predict = startPredict
        index = startIndex
        pstep = t_step[index]
    }

    sub decode_nibble(ubyte nibble) {
        word @zp difference = 0
        if nibble & 4
            difference += pstep
        pstep >>= 1
        if nibble & 2
            difference += pstep
        pstep >>= 1
        if nibble & 1
            difference += pstep
        pstep >>= 1
        difference += pstep
        if nibble & 8
            difference = -difference
        predict += difference as uword
        index += t_index[nibble]
        if index & 128
            index = 0
        else if index > 88
            index = 88
        pstep = t_step[index]
    }
}
