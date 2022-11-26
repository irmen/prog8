%import textio
%import floats
%option no_sysinit
%zeropage basicsafe

main {

    ubyte num_adpcm_blocks
    ubyte adpcm_blocks_left
    uword @requirezp nibblesptr

    sub start() {
        uword adpcm_size = &audiodata.adpcm_data_end - &audiodata.adpcm_data
        num_adpcm_blocks = (adpcm_size / 256) as ubyte      ; NOTE: THE ADPCM DATA NEEDS TO BE ENCODED IN 256-byte BLOCKS !

        txt.print_uw(adpcm_size)
        txt.print(" adpcm data size = ")
        txt.print_ub(num_adpcm_blocks)
        txt.print(" blocks\n(b)enchmark or (p)layback? ")

        when c64.CHRIN() {
            'b' -> benchmark()
            'p' -> playback()
        }
    }

    sub benchmark() {
        nibblesptr = &audiodata.adpcm_data

        txt.print("\ndecoding all blocks...\n")
        c64.SETTIM(0,0,0)
        repeat num_adpcm_blocks {
            adpcm.init(peekw(nibblesptr), @(nibblesptr+2))
            nibblesptr += 4
            repeat 252 {
               ubyte @zp nibble = @(nibblesptr)
               adpcm.decode_nibble(nibble & 15)     ; first word
               adpcm.decode_nibble(nibble>>4)       ; second word
               nibblesptr++
            }
        }
        float duration_secs = (c64.RDTIM16() as float) / 60.0
        float words_per_second = 505.0 * (num_adpcm_blocks as float) / duration_secs
        txt.print_uw(words_per_second as uword)
        txt.print(" words/sec\n")
    }

    sub playback() {
        nibblesptr = &audiodata.adpcm_data
        adpcm_blocks_left = num_adpcm_blocks

        cx16.VERA_AUDIO_CTRL = %10101111        ; mono 16 bit
        cx16.VERA_AUDIO_RATE = 0                ; halt playback
        repeat 1024 {
            cx16.VERA_AUDIO_DATA = 0
        }

        sys.set_irqd()
        cx16.CINV = &irq_handler
        cx16.VERA_IEN = %00001000               ; enable AFLOW
        sys.clear_irqd()

        cx16.VERA_AUDIO_RATE = 21               ; start playback at ~8000 hz

        float rate = (cx16.VERA_AUDIO_RATE as float) * (25e6 / 65536.0)
        txt.print("\naudio via irq at ")
        txt.print_uw(rate as uword)
        txt.print(" hz mono\n")

        repeat {
            ; audio will play via the IRQ.
        }

        ; not reached:
;        cx16.VERA_AUDIO_CTRL = %00100000
;        cx16.VERA_AUDIO_RATE = 0
;        txt.print("audio off.\n")
    }

    sub irq_handler() {
        if cx16.VERA_ISR & %00001000 {
            ; AFLOW irq.
    	    ;; cx16.vpoke(1,$fa0c, $a0)    ; paint a screen color

            ; refill the fifo buffer with two decoded adpcm blocks (252 nibbles -> 1008 bytes per block)
            repeat 2 {
                adpcm.init(peekw(nibblesptr), @(nibblesptr+2))
                nibblesptr += 4
                repeat 252 {
                   ubyte @zp nibble = @(nibblesptr)
                   adpcm.decode_nibble(nibble & 15)     ; first word
                   cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
                   cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
                   adpcm.decode_nibble(nibble>>4)       ; second word
                   cx16.VERA_AUDIO_DATA = lsb(adpcm.predict)
                   cx16.VERA_AUDIO_DATA = msb(adpcm.predict)
                   nibblesptr++
                }

                adpcm_blocks_left--
                if adpcm_blocks_left==0 {
                    ; restart adpcm data from the beginning
                    nibblesptr = &audiodata.adpcm_data
                    adpcm_blocks_left = num_adpcm_blocks
                }
            }

        } else {
            ; TODO not AFLOW, handle other IRQ
        }

        ;; cx16.vpoke(1,$fa0c, 0)      ; back to other screen color

        %asm {{
    	    ply
	    plx
	    pla
	    rti
        }}
    }

}

adpcm {

    ; IMA ADPCM decoder.
    ; https://wiki.multimedia.cx/index.php/IMA_ADPCM
    ; https://wiki.multimedia.cx/index.php/Microsoft_IMA_ADPCM

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
        ; this is the hotspot of the decoder algorithm!
        cx16.r0s = 0                ; difference
        if nibble & %0100
            cx16.r0s += pstep
        pstep >>= 1
        if nibble & %0010
            cx16.r0s += pstep
        pstep >>= 1
        if nibble & %0001
            cx16.r0s += pstep
        pstep >>= 1
        cx16.r0s += pstep
        if nibble & %1000
            cx16.r0s = -cx16.r0s
        predict += cx16.r0s as uword
        index += t_index[nibble]
        if index & 128
            index = 0
        else if index > len(t_step)-1
            index = len(t_step)-1
        pstep = t_step[index]
    }
}


audiodata {
    ;; %option align_page
adpcm_data:
    %asmbinary "adpcm-mono.bin"
adpcm_data_end:

}
