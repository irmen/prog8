;
; module to parse the header data of a .wav file
; big-endian version for 32 bits 68000 cpus
;
; note: the sample rate in hz can be converted to a vera rate via:
;        const float vera_freq_factor = 25e6 / 65536.0
;        vera_rate = (wavfile.sample_rate as float / vera_freq_factor) + 1.0 as ubyte
;        vera_rate_hz = (vera_rate as float) * vera_freq_factor as uword
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
}