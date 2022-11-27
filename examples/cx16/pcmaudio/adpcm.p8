%import textio
%import floats
%option no_sysinit
%zeropage basicsafe

;
; IMA ADPCM decoding and playback example.
; https://wiki.multimedia.cx/index.php/IMA_ADPCM
; https://wiki.multimedia.cx/index.php/Microsoft_IMA_ADPCM
;
; IMA ADPCM encodes two 16-bit PCM audio samples in 1 byte (1 word per nibble)
; thus compressing the audio data by a factor of 4.
; The encoding precision is about 13 bits per sample so it's a lossy compression scheme.
;
; NOTE:  this program requires 16 bits MONO audio, and 256 byte encoded block size!
; HOW TO CREATE SUCH IMA-ADPCM ENCODED AUDIO? Use sox or ffmpeg:
; $ sox --guard source.mp3 -r 8000 -c 1 -e ima-adpcm out.wav trim 01:27.50 00:09
; $ ffmpeg -i source.mp3 -ss 00:01:27.50 -to 00:01:36.50  -ar 8000 -ac 1 -c:a adpcm_ima_wav -block_size 256 -map_metadata -1 -bitexact out.wav
; Or use a tool such as https://github.com/dbry/adpcm-xq .
;

main {

    ubyte adpcm_blocks_left
    uword @requirezp nibblesptr

    sub start() {
        wavfile.parse()

        txt.print_uw(wavfile.adpcm_size)
        txt.print(" adpcm data size = ")
        txt.print_ub(wavfile.num_adpcm_blocks)
        txt.print(" blocks\nsamplerate = ")
        txt.print_uw(wavfile.sample_rate)
        txt.print(" vera rate = ")
        txt.print_uw(wavfile.vera_rate_hz)
        txt.print("\n(b)enchmark or (p)layback? ")

        when c64.CHRIN() {
            'b' -> benchmark()
            'p' -> playback()
        }
    }

    sub benchmark() {
        nibblesptr = wavfile.adpcm_data_ptr

        txt.print("\ndecoding all blocks...\n")
        c64.SETTIM(0,0,0)
        repeat wavfile.num_adpcm_blocks {
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
        float words_per_second = 505.0 * (wavfile.num_adpcm_blocks as float) / duration_secs
        txt.print_uw(words_per_second as uword)
        txt.print(" words/sec\n")
    }

    sub playback() {
        nibblesptr = wavfile.adpcm_data_ptr
        adpcm_blocks_left = wavfile.num_adpcm_blocks

        cx16.VERA_AUDIO_CTRL = %10101111        ; mono 16 bit
        cx16.VERA_AUDIO_RATE = 0                ; halt playback
        repeat 1024 {
            cx16.VERA_AUDIO_DATA = 0
        }

        sys.set_irqd()
        cx16.CINV = &irq_handler
        cx16.VERA_IEN = %00001000               ; enable AFLOW
        sys.clear_irqd()

        cx16.VERA_AUDIO_RATE = wavfile.vera_rate        ; start playback

        txt.print("\naudio via irq\n")

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
                    nibblesptr = wavfile.adpcm_data_ptr
                    adpcm_blocks_left = wavfile.num_adpcm_blocks
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


wavfile {

    const ubyte WAVE_FORMAT_PCM        =  $1
    const ubyte WAVE_FORMAT_ADPCM      =  $2
    const ubyte WAVE_FORMAT_IEEE_FLOAT =  $3
    const ubyte WAVE_FORMAT_ALAW       =  $6
    const ubyte WAVE_FORMAT_MULAW      =  $7
    const ubyte WAVE_FORMAT_DVI_ADPCM  =  $11

    uword sample_rate
    uword vera_rate_hz
    ubyte vera_rate
    uword adpcm_size
    uword adpcm_data_ptr
    ubyte num_adpcm_blocks

    sub parse() {
        ; "RIFF" , filesize (int32) , "WAVE", "fmt ", fmtsize (int32)
        ; we assume file sizes are <= 64Kb so don't have to worry about the upper 16 bits
        uword @zp header = &wavdata.wav_data
        if header[0]!=iso:'R' or header[1]!=iso:'I' or header[2]!=iso:'F' or header[3]!=iso:'F'
            or header[8]!=iso:'W' or header[9]!=iso:'A' or header[10]!=iso:'V' or header[11]!=iso:'E'
            or header[12]!=iso:'f' or header[13]!=iso:'m' or header[14]!=iso:'t' or header[15]!=iso:' ' {
            txt.print("not a valid wav file\n")
            sys.exit(1)
        }
        ; uword filesize = peekw(header+4)
        uword chunksize = peekw(header+16)
        uword wavefmt = peekw(header+20)
        uword nchannels = peekw(header+22)
        sample_rate = peekw(header+24)    ; we assume sample rate <= 65535 so we can ignore the upper word
        uword block_align = peekw(header+32)

        if block_align!=256 or nchannels!=1 or wavefmt!=WAVE_FORMAT_DVI_ADPCM {
            txt.print("invalid wav specs\n")
            sys.exit(1)
        }

        ; skip chunks until we reach the 'data' chunk
        header += chunksize + 20
        repeat {
            chunksize = peekw(header+4)        ; assume chunk size never exceeds 64kb so ignore upper word
            if header[0]==iso:'d' and header[1]==iso:'a' and header[2]==iso:'t' and header[3]==iso:'a'
                break
            header += 8 + chunksize
        }
        adpcm_data_ptr = header + 8
        adpcm_size = chunksize
        num_adpcm_blocks = (chunksize / 256) as ubyte      ; NOTE: THE ADPCM DATA NEEDS TO BE ENCODED IN 256-byte BLOCKS !

        const float vera_freq_factor = 25e6 / 65536.0
        vera_rate = (sample_rate as float / vera_freq_factor) + 1.0 as ubyte
        vera_rate_hz = (vera_rate as float) * vera_freq_factor as uword
    }
}

wavdata {
    %option align_page
wav_data:
    %asmbinary "adpcm-mono.wav"
wav_data_end:

}
