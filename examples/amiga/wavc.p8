; Convert a wave file to a raw PCM wave file.
; The input file is given as command-line argument.
; The converted file is written to "out.wav"
; Compile with:  prog8c -target amiga1200 examples/amiga/wavc.p8
;
; Supported input formats:
; - DVI/IMA ADPCM (format 17): decoded to 16-bit PCM
; - PCM (format 1): rewritten as a minimal PCM wav (only RIFF/fmt/data chunks)

%import dos
%import textio
%import wavfile
%import adpcm

main {
    ; Buffer for the wav file header (must be large enough to hold the complete header).
    const long HEADERSIZE = 4096
    ubyte[HEADERSIZE] wav_buffer

    ; The ADPCM decoder operates on fixed 256-byte input blocks.
    const uword ADPCM_BLOCK_SIZE = 256

    ; General-purpose copy buffer for PCM pass-through and ADPCM input chunks.
    const long BUFFERSIZE = 32768
    ubyte[BUFFERSIZE] copy_buffer

    ; Accumulation buffer for decoded PCM output. Flushed to disk when full.
    ; Must be at least as large as one decoded block (1010 bytes).
    const long OUTPUT_BUFFERSIZE = 32768
    ubyte[OUTPUT_BUFFERSIZE] output_buffer

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

        ; create the output file
        pointer outfile = dos.Open("out.wav", dos.MODE_NEWFILE)
        if outfile==0 {
            txt.print("cannot create output file out.wav\n")
            void dos.Close(file)
            return
        }

        if convert(file, outfile) {
            txt.print("conversion successful\n")
            void dos.Close(outfile)
        } else {
            txt.print("conversion failed\n")
            void dos.Close(outfile)
            void dos.DeleteFile("out.wav")      ; remove the incomplete output file
        }
        void dos.Close(file)
    }

    sub convert(pointer file, pointer outfile) -> bool {
        ; read enough data into memory to cover the complete header structure
        long header_bytes = dos.Read(file, &wav_buffer, HEADERSIZE)
        if header_bytes==0 or header_bytes<0 {
            txt.print("cannot read the file\n")
            return false
        }

        ; parse the header from the loaded data
        if not wavfile.parse_header(&wav_buffer) {
            txt.print("this does not appear to be a valid wav file\n")
            return false
        }

        print_header_info()

        when wavfile.wavefmt {
            wavfile.Format::DVI_ADPCM -> return convert_adpcm(file, outfile)
            wavfile.Format::PCM -> return convert_pcm(file, outfile)
            else -> {
                txt.print("unsupported audio format for conversion\n")
                return false
            }
        }
    }

    sub convert_adpcm(pointer file, pointer outfile) -> bool {
        ; This decoder only supports the 256-byte block size used by standard
        ; ffmpeg/sox-generated IMA-ADPCM files.
        if wavfile.block_align != ADPCM_BLOCK_SIZE {
            txt.print("error: ADPCM block size must be 256 bytes\n")
            return false
        }
        if (wavfile.data_size & 255) != 0 {
            txt.print("error: ADPCM data size is not a multiple of the block size\n")
            return false
        }
        if wavfile.nchannels != 1 and wavfile.nchannels != 2 {
            txt.print("error: only mono and stereo ADPCM are supported\n")
            return false
        }

        long blocks = wavfile.data_size / ADPCM_BLOCK_SIZE as long
        long pcm_data_size
        if wavfile.nchannels == 1
            pcm_data_size = blocks * 1010 as long
        else
            pcm_data_size = blocks * 996 as long

        ; write a standard PCM wav header (sizes patched after decoding)
        write_pcm_header(pcm_data_size)
        if dos.Write(outfile, &wav_buffer, 44 as long) != 44 {
            txt.print("cannot write the output header\n")
            return false
        }

        ; seek to the start of the ADPCM data
        if dos.Seek(file, wavfile.data_offset, dos.OFFSET_BEGINNING) < 0 {
            txt.print("cannot seek to the ADPCM data\n")
            return false
        }

        txt.print("decoding ")
        txt.print_l(blocks)
        txt.print(" ADPCM blocks...\n")
        long total_written = 0
        long output_offset = 0
        pointer outputptr = &output_buffer
        long remaining = wavfile.data_size
        while remaining > 0 {
            long chunk = remaining
            if chunk > BUFFERSIZE
                chunk = BUFFERSIZE
            long read_n = dos.Read(file, &copy_buffer, chunk)
            if read_n != chunk {
                txt.print("\nerror reading ADPCM chunk\n")
                return false
            }
            long blocks_in_chunk = chunk / (ADPCM_BLOCK_SIZE as long)
            long block_in_chunk = 0
            pointer chunkptr = &copy_buffer
            while block_in_chunk < blocks_in_chunk {
                long decoded_size
                if wavfile.nchannels == 1
                    decoded_size = 1010 as long
                else
                    decoded_size = 996 as long
                if output_offset + decoded_size > OUTPUT_BUFFERSIZE {
                    long write_n = dos.Write(outfile, &output_buffer, output_offset)
                    txt.chrout('.')
                    txt.flush()
                    if write_n != output_offset {
                        txt.print("\nerror writing the output file\n")
                        return false
                    }
                    total_written += write_n
                    output_offset = 0
                    outputptr = &output_buffer
                }
                if wavfile.nchannels == 1
                    adpcm.decode_block_mono(chunkptr, outputptr)
                else
                    adpcm.decode_block_stereo(chunkptr, outputptr)
                output_offset += decoded_size
                outputptr += decoded_size
                chunkptr += ADPCM_BLOCK_SIZE as long
                block_in_chunk++
            }
            remaining -= chunk
        }
        if output_offset > 0 {
            long flush_n = dos.Write(outfile, &output_buffer, output_offset)
            if flush_n != output_offset {
                txt.print("\nerror writing the output file\n")
                return false
            }
            total_written += flush_n
        }
        txt.nl()

        if total_written != pcm_data_size {
            txt.print("warning: decoded size mismatch\n")
        }

        if not patch_header_sizes(outfile, 36 as long + total_written, total_written)
            return false

        txt.print("done, wrote ")
        txt.print_l(total_written)
        txt.print(" bytes of decoded PCM to out.wav\n")
        return true
    }

    sub convert_pcm(pointer file, pointer outfile) -> bool {
        ; For PCM input, the sample data is already in the right format.
        ; Write a minimal PCM header and copy only the sample data, then patch
        ; the header sizes to match the amount of data actually written.
        write_pcm_header(wavfile.data_size)
        if dos.Write(outfile, &wav_buffer, 44 as long) != 44 {
            txt.print("cannot write the output header\n")
            return false
        }

        if dos.Seek(file, wavfile.data_offset, dos.OFFSET_BEGINNING) < 0 {
            txt.print("cannot seek to the PCM data\n")
            return false
        }

        txt.print("copying PCM data...\n")
        long total_written = 0
        while total_written < wavfile.data_size {
            long chunk = wavfile.data_size - total_written
            if chunk > BUFFERSIZE
                chunk = BUFFERSIZE
            long read_n = dos.Read(file, &copy_buffer, chunk)
            if read_n<=0 {
                txt.print("\ncannot read the input file\n")
                return false
            }
            long write_n = dos.Write(outfile, &copy_buffer, read_n)
            if write_n != read_n {
                txt.print("\ncannot write the output file\n")
                return false
            }
            total_written += write_n
            txt.chrout('.')
        }
        txt.nl()

        if not patch_header_sizes(outfile, 36 as long + total_written, total_written)
            return false

        txt.print("done, copied ")
        txt.print_l(total_written)
        txt.print(" bytes of PCM to out.wav\n")
        return true
    }

    sub patch_header_sizes(pointer outfile, long riff_size, long data_size) -> bool {
        ; patch the RIFF size field at offset 4
        write_le4(&wav_buffer, riff_size, 0)
        if dos.Seek(outfile, 4 as long, dos.OFFSET_BEGINNING) < 0 {
            txt.print("cannot seek in the output file\n")
            return false
        }
        if dos.Write(outfile, &wav_buffer, 4 as long) != 4 {
            txt.print("cannot update the output header\n")
            return false
        }
        ; patch the data chunk size field at offset 40
        write_le4(&wav_buffer, data_size, 0)
        if dos.Seek(outfile, 40 as long, dos.OFFSET_BEGINNING) < 0 {
            txt.print("cannot seek in the output file\n")
            return false
        }
        if dos.Write(outfile, &wav_buffer, 4 as long) != 4 {
            txt.print("cannot update the output header\n")
            return false
        }
        return true
    }

    sub write_pcm_header(long data_size) {
        ; create a standard 44-byte raw PCM wav header in wav_buffer:
        ; "RIFF", size, "WAVE", "fmt ", 16, format=PCM(1), channels, sample rate,
        ; byte rate, block align, bits per sample, "data", data size
        wav_buffer[0] = iso:'R'
        wav_buffer[1] = iso:'I'
        wav_buffer[2] = iso:'F'
        wav_buffer[3] = iso:'F'
        wav_buffer[8] = iso:'W'
        wav_buffer[9] = iso:'A'
        wav_buffer[10] = iso:'V'
        wav_buffer[11] = iso:'E'
        wav_buffer[12] = iso:'f'
        wav_buffer[13] = iso:'m'
        wav_buffer[14] = iso:'t'
        wav_buffer[15] = iso:' '
        wav_buffer[36] = iso:'d'
        wav_buffer[37] = iso:'a'
        wav_buffer[38] = iso:'t'
        wav_buffer[39] = iso:'a'

        uword pcm_block_align = (wavfile.nchannels as uword) * (wavfile.bits_per_sample as uword / 8)

        write_le4(&wav_buffer, 36 as long + data_size, 4)            ; RIFF size
        write_le2(&wav_buffer, 16, 16)                               ; fmt chunk size
        write_le2(&wav_buffer, 1, 20)                                ; audio format = PCM
        write_le2(&wav_buffer, wavfile.nchannels as uword, 22)       ; channels
        write_le4(&wav_buffer, wavfile.sample_rate as long, 24)      ; sample rate
        write_le4(&wav_buffer, (wavfile.sample_rate as long) * (pcm_block_align as long), 28)   ; byte rate
        write_le2(&wav_buffer, pcm_block_align, 32)                  ; block align
        write_le2(&wav_buffer, wavfile.bits_per_sample as uword, 34) ; bits per sample
        write_le4(&wav_buffer, data_size, 40)                        ; data chunk size
    }

    sub write_le2(pointer buf, uword value, uword offset) {
        ; write a 16-bit little-endian value into memory
        buf[offset] = value as ubyte
        buf[offset+1] = (value >> 8) as ubyte
    }

    sub write_le4(pointer buf, long value, uword offset) {
        ; write a 32-bit little-endian value into memory
        buf[offset] = value as ubyte
        buf[offset+1] = (value >> 8) as ubyte
        buf[offset+2] = (value >> 16) as ubyte
        buf[offset+3] = (value >> 24) as ubyte
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
}
