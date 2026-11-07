;
; module to parse the header data of a .wav file
; big-endian version for 32 bits 68000 cpus
;
; note (cx16 target): the sample rate in hz can be converted to a vera rate via:
;        vera_rate = (wavfile.sample_rate as long * 65536 / 25000000) as ubyte + 1
;        vera_rate_hz = (vera_rate as uword * 25000000 / 65536) as uword
;
;        or, the floating-point alternative:
;        const float vera_freq_factor = 25e6 / 65536.0
;        vera_rate = (wavfile.sample_rate as float / vera_freq_factor) + 1.0 as ubyte
;        vera_rate_hz = (vera_rate as float) * vera_freq_factor as uword
;
; note (amiga targets): the sample rate converts to a Paula period;
;        sound.set_freqvol(channel, sample_rate, volume) does the conversion
;        automatically with the machine's PAL or NTSC chip clock. Rates above
;        the ~28.8 kHz DMA ceiling should be resampled. 8-bit wav samples are
;        unsigned (silence = $80) but Paula plays signed bytes: the helpers
;        convert_8bit_mono() (mono, in place) and convert_8bit_stereo()
;        (stereo L/R split, sign-flipping both channels) do the conversion;
;        convert_16bit_stereo() splits 16-bit little-endian stereo
;        samples (already signed, so no flip) into two native big-endian
;        word buffers in the same pass;
;        convert_16bit_mono() converts 16-bit samples between little-endian
;        (WAV on disk) and native big-endian m68k order in place. Sample data
;        must be in chip RAM, lengths are word-based (pad odd samples), stereo
;        wavs must be de-interleaved into two buffers for two voices, and
;        samples start with a zero word.
;

wavfile {
    %option merge, ignore_unused

    enum Format {
        PCM        =  1,
        ADPCM      =  2,
        IEEE_FLOAT =  3,
        ALAW       =  6,
        MULAW      =  7,
        DVI_ADPCM  =  17,
    }

    uword sample_rate
    ubyte bits_per_sample
    long data_offset
    ubyte wavefmt
    ubyte nchannels
    uword block_align
    long data_size

    sub parse_header(^^ubyte wav_data) -> bool {
        ; "RIFF" , filesize (int32) , "WAVE", "fmt ", fmtsize (int32)
        ; keep the start address for the data_offset calculation and use a plain pointer for walking
        pointer start = wav_data
        pointer header = wav_data
        if header[0]!=iso:'R' or header[1]!=iso:'I' or header[2]!=iso:'F' or header[3]!=iso:'F'
            or header[8]!=iso:'W' or header[9]!=iso:'A' or header[10]!=iso:'V' or header[11]!=iso:'E'
            or header[12]!=iso:'f' or header[13]!=iso:'m' or header[14]!=iso:'t' or header[15]!=iso:' ' {
            return false
        }
        ; uword filesize = peekw(header+4)
        ; note: the wav data is little-endian, so read the words manually
        uword chunksize = read_le_word(header+16)
        wavefmt = peek(header+20)
        nchannels = peek(header+22)
        sample_rate = read_le_word(header+24)    ; we assume sample rate <= 65535 so we can ignore the upper word
        block_align = read_le_word(header+32)
        bits_per_sample = peek(header+34)
        if wavefmt==Format::DVI_ADPCM or wavefmt==Format::ADPCM
            bits_per_sample *= 4

        ; skip chunks until we reach the 'data' chunk, but limit to avoid going off the end
        header += chunksize + 20
        repeat 20 {
            chunksize = read_le_word(header+4)        ; assume chunk size never exceeds 64kb so ignore upper word
            if header[0]==iso:'d' and header[1]==iso:'a' and header[2]==iso:'t' and header[3]==iso:'a' {
                data_size = mklong2(read_le_word(header+6), chunksize)
                data_offset = header as long + 8 - start as long
                return true
            }
            header += 8 + chunksize
        }
        return false
    }

    sub read_le_word(pointer addr) -> uword {
        ; read a 16-bit little-endian value from memory
        return ((addr[1] as uword) << 8) | addr[0] as uword
    }

    ; ===== PCM sample helpers (Paula playback preparation) =====
    ; wavfile only parses headers; these transform the sample bytes so they
    ; can be handed to the sound library's play()/loop()/stream_start().
    ; WAV 8-bit PCM is unsigned (silence = $80); Paula plays signed bytes.

    asmsub convert_8bit_mono(pointer buf @A0, long len @D0) {
        ; In-place: convert unsigned 8-bit WAV PCM to signed Paula PCM
        ; (xor each byte with $80). len = number of bytes.
        ; Mono files: call this once on the whole sample buffer.
        %asm {{
            tst.l   d0
            beq.s   .done
.loop:
            eori.b  #$80,(a0)+
            subq.l  #1,d0
            bne.s   .loop
.done:
            rts
        }}
    }

    asmsub convert_8bit_stereo(pointer src @A0, pointer dst_left @A1, pointer dst_right @A2, long nframes @D0) {
        ; Split interleaved L/R 8-bit samples into two separate buffers,
        ; sign-flipping each sample. nframes = number of (L,R) pairs;
        ; total bytes consumed = nframes*2. The destination buffers must be
        ; distinct from src and from each other. Stereo files: call this
        ; instead of convert_8bit_mono - it does the sign flip for both channels.
        %asm {{
            movem.l d1-d3/a2,-(sp)
            tst.l   d0
            beq.s   .done
            move.l  d0,d3
.loop:
            move.b  (a0)+,d1
            eori.b  #$80,d1
            move.b  d1,(a1)+
            move.b  (a0)+,d2
            eori.b  #$80,d2
            move.b  d2,(a2)+
            subq.l  #1,d3
            bne.s   .loop
.done:
            movem.l (sp)+,d1-d3/a2
            rts
        }}
    }

    asmsub convert_16bit_stereo(pointer src @A0, pointer dst_left @A1, pointer dst_right @A2, long nframes @D0) {
        ; Split interleaved 16-bit (little-endian) stereo samples into two
        ; separate buffers, converting each sample to native big-endian m68k
        ; order in the same pass (16-bit WAV is already signed, so no sign
        ; flip is needed). nframes = number of (L,R) pairs; total bytes
        ; consumed = nframes*4, each destination gets nframes*2 bytes.
        ; Destination buffers must be distinct from src and from each other.
        ; No separate convert_16bit_mono() call is needed afterwards.
        %asm {{
            movem.l d1-d3/a2,-(sp)
            tst.l   d0
            beq.s   .done
            move.l  d0,d3
.loop:
            move.b  (a0)+,d1        ; left low
            move.b  (a0)+,d2        ; left high
            move.b  d2,(a1)+        ; native (big-endian) high first
            move.b  d1,(a1)+        ; then low
            move.b  (a0)+,d1        ; right low
            move.b  (a0)+,d2        ; right high
            move.b  d2,(a2)+
            move.b  d1,(a2)+
            subq.l  #1,d3
            bne.s   .loop
.done:
            movem.l (sp)+,d1-d3/a2
            rts
        }}
    }

    asmsub deinterleave_16bit_stereo_be(pointer src @A0, pointer dst_left @A1, pointer dst_right @A2, long nframes @D0) {
        ; Split interleaved 16-bit big-endian (native m68k) stereo samples into two
        ; separate buffers without byte-swapping (for ADPCM decoder output, which is
        ; already native order). nframes = number of (L,R) pairs; total bytes
        ; consumed = nframes*4, each destination gets nframes*2 bytes.
        ; Destination buffers must be distinct from src and from each other.
        %asm {{
            move.l  a2,-(sp)
            tst.l   d0
            beq.s   .done
.loop:
            move.w  (a0)+,(a1)+
            move.w  (a0)+,(a2)+
            subq.l  #1,d0
            bne.s   .loop
.done:
            move.l  (sp)+,a2
            rts
        }}
    }

    asmsub convert_16bit_mono(pointer buf @A0, long nwords @D0) {
        ; In-place: swap the two bytes of each 16-bit word, converting
        ; little-endian WAV sample data (on disk) to native big-endian m68k
        ; order (and back). nwords = number of 16-bit words. Used by file
        ; converters (wavc) and by 16-bit playback preparation.
        %asm {{
            movem.l d1-d2,-(sp)
            tst.l   d0
            beq.s   .done
.loop:
            move.b  (a0),d1
            move.b  1(a0),d2
            move.b  d2,(a0)
            move.b  d1,1(a0)
            addq.l  #2,a0
            subq.l  #1,d0
            bne.s   .loop
.done:
            movem.l (sp)+,d1-d2
            rts
        }}
    }
}
