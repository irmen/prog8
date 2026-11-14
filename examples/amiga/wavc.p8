; Convert a wave file to amiga-playable raw PCM.
; The input file is given as command-line argument.
; Output: "out.pcm" (mono) or "out_left.pcm" + "out_right.pcm" (stereo).
; Compile with:  prog8c -target amiga1200 examples/amiga/wavc.p8
;
; Supported input formats:
; - 8-bit PCM (mono or stereo): sign-flipped to signed 8-bit Paula PCM
; - 16-bit PCM (mono or stereo): byte-swapped to big-endian m68k order
; - DVI/IMA ADPCM (format 17): decoded to 16-bit PCM, then byte-swapped

%import dos
%import textio
%import wavfile
%import adpcm

main {

    ; Buffer for the wav file header (must be large enough to hold the complete header).
    const long HEADERSIZE = 4096
    ubyte[HEADERSIZE] wav_buffer

    ; General-purpose input copy buffer for PCM pass-through and ADPCM input chunks.
    const long BUFFERSIZE = 32768
    ubyte[BUFFERSIZE] copy_buffer

    ; Accumulation buffer for decoded ADPCM PCM output. Flushed to disk when full.
    ; Must be at least as large as one decoded block (1010 bytes mono, 996 stereo).
    const long OUTPUT_BUFFERSIZE = 32768
    ubyte[OUTPUT_BUFFERSIZE] output_buffer

    ; Scratch buffers for stereo deinterleaving (half of BUFFERSIZE each).
    const long STEREO_BUFSIZE = 16384
    ubyte[STEREO_BUFSIZE] left_buffer
    ubyte[STEREO_BUFSIZE] right_buffer

    ; ADPCM decoder operates on fixed 256-byte input blocks.
    const uword ADPCM_BLOCK_SIZE = 256

    sub start() {
        txt.print("wavc - wav file converter to amiga-playable PCM\n")
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

        ; read enough data into memory to cover the complete header structure
        long header_bytes = dos.Read(file, &wav_buffer, HEADERSIZE)
        if header_bytes < 44 {
            ; 44 bytes is the minimum for a valid RIFF/WAVE/fmt/data header;
            ; a short read here means a truncated or unreadable file
            txt.print("cannot read the file\n")
            void dos.Close(file)
            return
        }

        ; parse the header from the loaded data
        if not wavfile.parse_header(&wav_buffer) {
            txt.print("this does not appear to be a valid wav file\n")
            void dos.Close(file)
            return
        }

        print_header_info()

        ; seek to the start of the audio data
        if dos.Seek(file, wavfile.data_offset, dos.OFFSET_BEGINNING) < 0 {
            txt.print("cannot seek to audio data\n")
            void dos.Close(file)
            return
        }

        bool ok = false
        when wavfile.wavefmt {
            wavfile.Format::PCM -> {
                if wavfile.bits_per_sample == 8 {
                    if wavfile.nchannels == 1
                        ok = convert_pcm_8_mono(file)
                    else if wavfile.nchannels == 2
                        ok = convert_pcm_8_stereo(file)
                    else
                        txt.print("unsupported channel count\n")
                } else if wavfile.bits_per_sample == 16 {
                    if wavfile.nchannels == 1
                        ok = convert_pcm_16_mono(file)
                    else if wavfile.nchannels == 2
                        ok = convert_pcm_16_stereo(file)
                    else
                        txt.print("unsupported channel count\n")
                } else {
                    txt.print("unsupported bit depth for PCM\n")
                }
            }
            wavfile.Format::DVI_ADPCM -> {
                if wavfile.nchannels == 1
                    ok = convert_adpcm_mono(file)
                else if wavfile.nchannels == 2
                    ok = convert_adpcm_stereo(file)
                else
                    txt.print("unsupported channel count for ADPCM\n")
            }
            else -> txt.print("unsupported audio format for conversion\n")
        }

        if ok
            txt.print("conversion successful\n")
        else
            txt.print("conversion failed\n")

        void dos.Close(file)
    }

    ; ---------- 8-bit PCM mono ----------
    sub convert_pcm_8_mono(pointer file) -> bool {
        ; convert unsigned 8-bit PCM to signed (amiga-playable) in place
        pointer outfile = dos.Open("out.pcm", dos.MODE_NEWFILE)
        if outfile==0 {
            txt.print("cannot create out.pcm\n")
            return false
        }
        txt.print("converting 8-bit mono PCM...\n")
        long total = wavfile.data_size
        long done = 0
        while done < total {
            long chunk = total - done
            if chunk > BUFFERSIZE
                chunk = BUFFERSIZE
            long read_n = dos.Read(file, &copy_buffer, chunk)
            if read_n <= 0 {
                txt.print("\ncannot read input\n")
                void dos.Close(outfile)
                return false
            }
            wavfile.convert_8bit_mono(&copy_buffer, read_n)
            long write_n = dos.Write(outfile, &copy_buffer, read_n)
            if write_n != read_n {
                txt.print("\ncannot write output\n")
                void dos.Close(outfile)
                return false
            }
            done += write_n
            txt.chrout('.')
        }
        txt.nl()
        txt.print("done, wrote ")
        txt.print_l(done)
        txt.print(" bytes to out.pcm\n")
        void dos.Close(outfile)
        return true
    }

    ; ---------- 8-bit PCM stereo ----------
    sub convert_pcm_8_stereo(pointer file) -> bool {
        ; deinterleave + sign-flip into left/right buffers
        pointer outl = dos.Open("out_left.pcm", dos.MODE_NEWFILE)
        if outl==0 {
            txt.print("cannot create out_left.pcm\n")
            return false
        }
        pointer outr = dos.Open("out_right.pcm", dos.MODE_NEWFILE)
        if outr==0 {
            txt.print("cannot create out_right.pcm\n")
            void dos.Close(outl)
            return false
        }
        txt.print("converting 8-bit stereo PCM...\n")
        long total_frames = wavfile.data_size / 2 as long
        long done_frames = 0
        while done_frames < total_frames {
            long frames = (total_frames - done_frames)
            const long limit = BUFFERSIZE / 2
            if frames > limit
                frames = limit
            long bytes = frames * 2
            long read_n = dos.Read(file, &copy_buffer, bytes)
            if read_n != bytes {
                txt.print("\ncannot read input\n")
                void dos.Close(outl)
                void dos.Close(outr)
                return false
            }
            wavfile.convert_8bit_stereo(&copy_buffer, &left_buffer, &right_buffer, frames)
            long wl = dos.Write(outl, &left_buffer, frames)
            long wr = dos.Write(outr, &right_buffer, frames)
            if wl != frames or wr != frames {
                txt.print("\ncannot write output\n")
                void dos.Close(outl)
                void dos.Close(outr)
                return false
            }
            done_frames += frames
            txt.chrout('.')
        }
        txt.nl()
        txt.print("done, wrote ")
        txt.print_l(done_frames)
        txt.print(" frames to out_left.pcm / out_right.pcm\n")
        void dos.Close(outl)
        void dos.Close(outr)
        return true
    }

    ; ---------- 16-bit PCM mono ----------
    sub convert_pcm_16_mono(pointer file) -> bool {
        ; byte-swap to native big-endian in place
        pointer outfile = dos.Open("out.pcm", dos.MODE_NEWFILE)
        if outfile==0 {
            txt.print("cannot create out.pcm\n")
            return false
        }
        txt.print("converting 16-bit mono PCM...\n")
        long total = wavfile.data_size
        long done = 0
        while done < total {
            long chunk = total - done
            if chunk > BUFFERSIZE
                chunk = BUFFERSIZE
            ; ensure word-aligned
            if (chunk & 1) != 0
                chunk -= 1
            if chunk == 0
                break
            long read_n = dos.Read(file, &copy_buffer, chunk)
            if read_n != chunk {
                txt.print("\ncannot read input\n")
                void dos.Close(outfile)
                return false
            }
            wavfile.convert_16bit_mono(&copy_buffer, (read_n / 2) as long)
            long write_n = dos.Write(outfile, &copy_buffer, read_n)
            if write_n != read_n {
                txt.print("\ncannot write output\n")
                void dos.Close(outfile)
                return false
            }
            done += write_n
            txt.chrout('.')
        }
        txt.nl()
        txt.print("done, wrote ")
        txt.print_l(done)
        txt.print(" bytes to out.pcm\n")
        void dos.Close(outfile)
        return true
    }

    ; ---------- 16-bit PCM stereo ----------
    sub convert_pcm_16_stereo(pointer file) -> bool {
        ; deinterleave + byte-swap into left/right buffers
        pointer outl = dos.Open("out_left.pcm", dos.MODE_NEWFILE)
        if outl==0 {
            txt.print("cannot create out_left.pcm\n")
            return false
        }
        pointer outr = dos.Open("out_right.pcm", dos.MODE_NEWFILE)
        if outr==0 {
            txt.print("cannot create out_right.pcm\n")
            void dos.Close(outl)
            return false
        }
        txt.print("converting 16-bit stereo PCM...\n")
        long total_frames = wavfile.data_size / 4 as long
        long done_frames = 0
        while done_frames < total_frames {
            long frames = (total_frames - done_frames)
            const long limit = BUFFERSIZE / 4
            if frames > limit
                frames = limit
            long bytes = frames * 4
            long read_n = dos.Read(file, &copy_buffer, bytes)
            if read_n != bytes {
                txt.print("\ncannot read input\n")
                void dos.Close(outl)
                void dos.Close(outr)
                return false
            }
            wavfile.convert_16bit_stereo(&copy_buffer, &left_buffer, &right_buffer, frames)
            long wl = dos.Write(outl, &left_buffer, frames * 2)
            long wr = dos.Write(outr, &right_buffer, frames * 2)
            if wl != frames * 2 or wr != frames * 2 {
                txt.print("\ncannot write output\n")
                void dos.Close(outl)
                void dos.Close(outr)
                return false
            }
            done_frames += frames
            txt.chrout('.')
        }
        txt.nl()
        txt.print("done, wrote ")
        txt.print_l(done_frames)
        txt.print(" frames to out_left.pcm / out_right.pcm\n")
        void dos.Close(outl)
        void dos.Close(outr)
        return true
    }

    ; ---------- DVI/IMA ADPCM mono ----------
    sub convert_adpcm_mono(pointer file) -> bool {
        if wavfile.block_align != ADPCM_BLOCK_SIZE {
            txt.print("error: ADPCM block size must be 256 bytes\n")
            return false
        }
        long blocks = wavfile.data_size / ADPCM_BLOCK_SIZE as long
        long remainder = wavfile.data_size % ADPCM_BLOCK_SIZE as long
        if remainder != 0 {
            txt.print("note: skipping partial final block of ")
            txt.print_l(remainder)
            txt.print(" bytes\n")
        }
        long pcm_data_size = blocks * 1010 as long

        pointer outfile = dos.Open("out.pcm", dos.MODE_NEWFILE)
        if outfile==0 {
            txt.print("cannot create out.pcm\n")
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
                void dos.Close(outfile)
                return false
            }
            long blocks_in_chunk = chunk / (ADPCM_BLOCK_SIZE as long)
            long block_in_chunk = 0
            pointer chunkptr = &copy_buffer
            while block_in_chunk < blocks_in_chunk {
                const long decoded_size = 1010 as long
                if output_offset + decoded_size > OUTPUT_BUFFERSIZE {
                    long write_n = dos.Write(outfile, &output_buffer, output_offset)
                    txt.chrout('.')
                    txt.flush()
                    if write_n != output_offset {
                        txt.print("\nerror writing the output file\n")
                        void dos.Close(outfile)
                        return false
                    }
                    total_written += write_n
                    output_offset = 0
                    outputptr = &output_buffer
                }
                adpcm.decode_block_mono(chunkptr, outputptr)
                ; decoder output is already native big-endian; write directly.
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
                void dos.Close(outfile)
                return false
            }
            total_written += flush_n
        }
        txt.nl()
        if total_written != pcm_data_size {
            txt.print("warning: decoded size mismatch\n")
        }
        txt.print("done, wrote ")
        txt.print_l(total_written)
        txt.print(" bytes to out.pcm\n")
        void dos.Close(outfile)
        return true
    }

    ; ---------- DVI/IMA ADPCM stereo ----------
    sub convert_adpcm_stereo(pointer file) -> bool {
        if wavfile.block_align != ADPCM_BLOCK_SIZE {
            txt.print("error: ADPCM block size must be 256 bytes\n")
            return false
        }
        if wavfile.nchannels != 2 {
            txt.print("error: expected stereo ADPCM\n")
            return false
        }
        long blocks = wavfile.data_size / ADPCM_BLOCK_SIZE as long
        long remainder = wavfile.data_size % ADPCM_BLOCK_SIZE as long
        if remainder != 0 {
            txt.print("note: skipping partial final block of ")
            txt.print_l(remainder)
            txt.print(" bytes\n")
        }

        pointer outl = dos.Open("out_left.pcm", dos.MODE_NEWFILE)
        if outl==0 {
            txt.print("cannot create out_left.pcm\n")
            return false
        }
        pointer outr = dos.Open("out_right.pcm", dos.MODE_NEWFILE)
        if outr==0 {
            txt.print("cannot create out_right.pcm\n")
            void dos.Close(outl)
            return false
        }
        txt.print("decoding ")
        txt.print_l(blocks)
        txt.print(" ADPCM blocks...\n")
        long total_written = 0
        long remaining = wavfile.data_size
        while remaining > 0 {
            long chunk = remaining
            if chunk > BUFFERSIZE
                chunk = BUFFERSIZE
            long read_n = dos.Read(file, &copy_buffer, chunk)
            if read_n != chunk {
                txt.print("\nerror reading ADPCM chunk\n")
                void dos.Close(outl)
                void dos.Close(outr)
                return false
            }
            long blocks_in_chunk = chunk / (ADPCM_BLOCK_SIZE as long)
            long block_in_chunk = 0
            pointer chunkptr = &copy_buffer
            while block_in_chunk < blocks_in_chunk {
                const long decoded_size = 996 as long
                adpcm.decode_block_stereo(chunkptr, &output_buffer)
                ; decoder output is already native big-endian; deinterleave only.
                wavfile.deinterleave_16bit_stereo_be(&output_buffer, &left_buffer, &right_buffer, (decoded_size / 4) as long)
                const long half = decoded_size / 2 as long
                long wl = dos.Write(outl, &left_buffer, half)
                long wr = dos.Write(outr, &right_buffer, half)
                if wl != half or wr != half {
                    txt.print("\nerror writing the output file\n")
                    void dos.Close(outl)
                    void dos.Close(outr)
                    return false
                }
                total_written += half
                chunkptr += ADPCM_BLOCK_SIZE as long
                block_in_chunk++
                txt.chrout('.')
                txt.flush()
            }
            remaining -= chunk
        }
        txt.nl()
        txt.print("done, wrote ")
        txt.print_l(total_written)
        txt.print(" bytes per channel to out_left.pcm / out_right.pcm\n")
        void dos.Close(outl)
        void dos.Close(outr)
        return true
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
