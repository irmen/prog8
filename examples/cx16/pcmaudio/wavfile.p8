;
; module to parse the header data of a .wav file
;
; note: the sample rate in hz can be converted to a vera rate via:
;        const float vera_freq_factor = 25e6 / 65536.0
;        vera_rate = (wavfile.sample_rate as float / vera_freq_factor) + 1.0 as ubyte
;        vera_rate_hz = (vera_rate as float) * vera_freq_factor as uword
;

wavfile {

    const ubyte WAVE_FORMAT_PCM        =  $1
    const ubyte WAVE_FORMAT_ADPCM      =  $2
    const ubyte WAVE_FORMAT_IEEE_FLOAT =  $3
    const ubyte WAVE_FORMAT_ALAW       =  $6
    const ubyte WAVE_FORMAT_MULAW      =  $7
    const ubyte WAVE_FORMAT_DVI_ADPCM  =  $11

    uword sample_rate
    ubyte bits_per_sample
    uword data_offset
    ubyte wavefmt
    ubyte nchannels
    uword block_align
    long data_size

    sub parse_header(uword wav_data) -> bool {
        ; "RIFF" , filesize (int32) , "WAVE", "fmt ", fmtsize (int32)
        uword @zp header = wav_data
        if header[0]!=iso:'R' or header[1]!=iso:'I' or header[2]!=iso:'F' or header[3]!=iso:'F'
            or header[8]!=iso:'W' or header[9]!=iso:'A' or header[10]!=iso:'V' or header[11]!=iso:'E'
            or header[12]!=iso:'f' or header[13]!=iso:'m' or header[14]!=iso:'t' or header[15]!=iso:' ' {
            return false
        }
        ; uword filesize = peekw(header+4)
        uword chunksize = peekw(header+16)
        wavefmt = peek(header+20)
        nchannels = peek(header+22)
        sample_rate = peekw(header+24)    ; we assume sample rate <= 65535 so we can ignore the upper word
        block_align = peekw(header+32)
        bits_per_sample = peek(header+34)
        if wavefmt==WAVE_FORMAT_DVI_ADPCM or wavefmt==WAVE_FORMAT_ADPCM
            bits_per_sample *= 4

        ; skip chunks until we reach the 'data' chunk
        header += chunksize + 20
        repeat {
            chunksize = peekw(header+4)        ; assume chunk size never exceeds 64kb so ignore upper word
            if header[0]==iso:'d' and header[1]==iso:'a' and header[2]==iso:'t' and header[3]==iso:'a'
                break
            header += 8 + chunksize
        }

        data_size = mklong2(peekw(header+6), chunksize)
        data_offset = header + 8 - wav_data
        return true
    }
}
