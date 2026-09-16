; BUG: THE DEFERS DON'T ALWAYS TRIGGER CORRECTLY ON PROGRAM EXIT ON Amiga

%import dos
%import textio
%import wavfile

main {
    ; buffer holding the wav file's header and data chunks
    ubyte[4096] wav_buffer

    sub start() {
        txt.print("wavc - wav file converter\n")
        txt.print("input: ")
        txt.print(dos.GetArgStr())
        txt.nl()

        ; open the input wave file (given as first arg on the command line)
        pointer file = dos.Open(dos.GetArgStr(), dos.MODE_OLDFILE)
        if file==0 {
            txt.print("cannot open the input file\n")
            txt.print("usage: wavc <input.wav>\n")
            return
        }
        defer void dos.Close(file)

        ; read enough data into memory to cover the complete header structure
        long header_bytes = dos.Read(file, &wav_buffer, 4096 as long)
        if header_bytes==0 or header_bytes<0 {
            txt.print("cannot read the file\n")
            return
        }

        ; parse the header from the loaded data
        if not wavfile.parse_header(&wav_buffer) {
            txt.print("this does not appear to be a valid wav file\n")
            return
        }

        print_header_info()

        ; --- convert to a wav file, written to "out.wav" ---
        ; note: for now the output is a byte-exact 1:1 copy of the input file,
        ; because no ADPCM decoder is available yet on the m68k targets.
        ; once the decoder exists, make_pcm_header() below can be used to write
        ; a proper raw PCM header and the decode loop replaces the plain copy.

        ; make_pcm_header()

        pointer outfile = dos.Open("out.wav", dos.MODE_NEWFILE)
        if outfile==0 {
            txt.print("cannot create output file out.wav\n")
            return
        }
        defer void dos.Close(outfile)

        ; write the header bytes that were already read, unchanged
        if dos.Write(outfile, &wav_buffer, header_bytes) != header_bytes {
            txt.print("cannot write the output file\n")
            return
        }

        ; copy the remainder of the input file, chunk by chunk (1:1 copy)
        txt.print("copying...\n")
        txt.cursor_off()
        defer txt.cursor_on()

        long left = wavfile.data_size
        const long CHUNKSIZE = 32768
        repeat {
            long read_n = dos.Read(file, &wav_buffer, CHUNKSIZE)
            if read_n==0
                break
            if read_n<0 {
                txt.print("\ncannot read the input file\n")
                return
            }
            long write_n = dos.Write(outfile, &wav_buffer, read_n)
            if write_n != read_n {
                txt.print("\ncannot write the output file\n")
                return
            }
            left -= write_n
            txt.chrout('\r')
            txt.print_l(left)
            txt.chrout(' ')
        }
        txt.nl()

        txt.print("done, wrote byte-exact 1:1 copy to out.wav\n")
    }

    sub print_header_info() {
        ; print all the information that was gathered
        txt.print("audio format: ")
        txt.print_ub(wavfile.wavefmt)
        txt.print(" (")
        print_format_name()
        txt.print(")\n")
        txt.print("channels: ")
        txt.print_ub(wavfile.nchannels)
        txt.nl()
        txt.print("sample rate: ")
        txt.print_uw(wavfile.sample_rate)
        txt.print(" hz\n")
        txt.print("bits per sample: ")
        txt.print_ub(wavfile.bits_per_sample)
        txt.nl()
        txt.print("block align: ")
        txt.print_uw(wavfile.block_align)
        txt.print(" bytes\n")
        txt.print("audio data size: ")
        txt.print_l(wavfile.data_size)
        txt.print(" bytes\n")
        txt.print("audio data offset in file: ")
        txt.print_l(wavfile.data_offset)
        txt.print(" bytes\n")
    }

    sub print_format_name() {
        when wavfile.wavefmt {
            wavfile.Format::PCM -> txt.print("PCM")
            wavfile.Format::ADPCM -> txt.print("ADPCM")
            wavfile.Format::IEEE_FLOAT -> txt.print("IEEE float")
            wavfile.Format::ALAW -> txt.print("a-law")
            wavfile.Format::MULAW -> txt.print("mu-law")
            wavfile.Format::DVI_ADPCM -> txt.print("DVI ADPCM")
            else -> txt.print("unknown")
        }
    }

    ; ---- conversion helpers, not used yet (waiting for the ADPCM decoder) ----

    ; sub make_pcm_header() {
    ;     ; create a standard 44-byte raw PCM wav header in wav_buffer:
    ;     ; "RIFF", size, "WAVE", "fmt ", 16, format=PCM(1), channels, sample rate,
    ;     ; byte rate, block align, bits per sample, "data", data size
    ;     ; the size fields are filled in correctly at the end (seek back)
    ;     wav_buffer[0] = iso:'R'
    ;     wav_buffer[1] = iso:'I'
    ;     wav_buffer[2] = iso:'F'
    ;     wav_buffer[3] = iso:'F'
    ;     wav_buffer[8] = iso:'W'
    ;     wav_buffer[9] = iso:'A'
    ;     wav_buffer[10] = iso:'V'
    ;     wav_buffer[11] = iso:'E'
    ;     wav_buffer[12] = iso:'f'
    ;     wav_buffer[13] = iso:'m'
    ;     wav_buffer[14] = iso:'t'
    ;     wav_buffer[15] = iso:' '
    ;     wav_buffer[36] = iso:'d'
    ;     wav_buffer[37] = iso:'a'
    ;     wav_buffer[38] = iso:'t'
    ;     wav_buffer[39] = iso:'a'
    ;
    ;     uword pcm_block_align = (wavfile.nchannels as uword) * (wavfile.bits_per_sample as uword / 8)
    ;
    ;     write_le4(&wav_buffer, 0 as long, 4)            ; RIFF size (placeholder, patched later)
    ;     write_le2(&wav_buffer, 16, 16)                  ; fmt chunk size
    ;     write_le2(&wav_buffer, 1, 20)                   ; audio format = PCM
    ;     write_le2(&wav_buffer, wavfile.nchannels as uword, 22)      ; channels
    ;     write_le4(&wav_buffer, wavfile.sample_rate as long, 24)     ; sample rate
    ;     write_le4(&wav_buffer, (wavfile.sample_rate as long) * (pcm_block_align as long), 28)   ; byte rate
    ;     write_le2(&wav_buffer, pcm_block_align, 32)     ; block align
    ;     write_le2(&wav_buffer, wavfile.bits_per_sample as uword, 34)  ; bits per sample
    ;     write_le4(&wav_buffer, 0 as long, 40)           ; data size (placeholder, patched later)
    ; }
    ;
    ; sub write_le2(pointer buf, uword value, uword offset) {
    ;     ; write a 16-bit little-endian value into memory
    ;     buf[offset] = value as ubyte
    ;     buf[offset+1] = (value >> 8) as ubyte
    ; }
    ;
    ; sub write_le4(pointer buf, long value, uword offset) {
    ;     ; write a 32-bit little-endian value into memory
    ;     buf[offset] = value as ubyte
    ;     buf[offset+1] = (value >> 8) as ubyte
    ;     buf[offset+2] = (value >> 16) as ubyte
    ;     buf[offset+3] = (value >> 24) as ubyte
    ; }
}
