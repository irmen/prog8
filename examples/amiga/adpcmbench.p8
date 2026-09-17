%import dos
%import textio
%import timer
%import wavfile
%import adpcm

main {
    ; Large in-memory buffer to hold the entire source WAV file.
    const long WAV_BUFFER_SIZE = 65536
    pointer wav_buffer = memory("wavdata", WAV_BUFFER_SIZE, 0)

    ; The ADPCM decoder operates on fixed 256-byte input blocks.
    const uword ADPCM_BLOCK_SIZE = 256

    ; Small scratch buffer large enough to hold one decoded block
    ; (1010 bytes for mono, 996 bytes for stereo).
    const uword DECODED_BLOCK_SIZE = 1010
    ubyte[DECODED_BLOCK_SIZE] decoded_block

    ; Size of the decoded PCM output, computed during decoding.
    long decoded_pcm_size

    sub start() {
        if not timer.opendevice() {
            txt.print("cannot open timer.device\n")
            return
        }

        run_benchmark("small-adpcm-mono.wav")
        run_benchmark("small-adpcm-stereo.wav")
        timer.closedevice()
    }

    sub run_benchmark(str filename) {
        txt.print("\nadpcmbench loading ")
        txt.print(filename)
        txt.print("\n")

        long load_s, load_us = timer.getsystime()
        pointer file = dos.Open(filename, dos.MODE_OLDFILE)
        if file==0 {
            txt.print("cannot open the input file\n")
            return
        }
        long file_size = dos.Read(file, wav_buffer, WAV_BUFFER_SIZE)
        void dos.Close(file)
        long load_end_s, load_end_us = timer.getsystime()

        txt.print("loaded ")
        txt.print_l(file_size)
        txt.print(" bytes\n")

        if file_size <= 0 {
            txt.print("cannot read the file\n")
            return
        }
        if file_size >= WAV_BUFFER_SIZE {
            txt.print("error: wav file is too large for the buffer\n")
            return
        }

        long decode_s, decode_us = timer.getsystime()
        bool decode_ok = benchmark(file_size)
        long decode_end_s, decode_end_us = timer.getsystime()

        long load_duration = duration_us(load_s, load_us, load_end_s, load_end_us)
        long decode_duration = duration_us(decode_s, decode_us, decode_end_s, decode_end_us)

        txt.print("\nload:   ")
        print_duration(load_duration)
        txt.print(", ")
        print_throughput(file_size, load_duration)
        txt.nl()

        if decode_ok {
            txt.print("decode: ")
            print_duration(decode_duration)
            txt.print(", input ")
            print_throughput(wavfile.data_size, decode_duration)
            txt.print(", decoded ")
            print_throughput(decoded_pcm_size, decode_duration)
            txt.print(", ")
            print_sample_rate(decoded_pcm_size, decode_duration)
            txt.nl()
        } else {
            txt.print("decode: failed\n")
        }
    }

    sub benchmark(long file_size) -> bool {
        if not wavfile.parse_header(wav_buffer) {
            txt.print("this does not appear to be a valid wav file\n")
            return false
        }

        print_header_info()

        if wavfile.wavefmt==wavfile.Format::DVI_ADPCM
            return decode_adpcm(file_size)
        txt.print("unsupported audio format for conversion\n")
        return false
    }

    sub decode_adpcm(long file_size) -> bool {
        decoded_pcm_size = 0
        ; This decoder only supports the 256-byte block size used by standard
        ; ffmpeg/sox-generated IMA-ADPCM files.
        if wavfile.block_align != ADPCM_BLOCK_SIZE {
            txt.print("error: ADPCM block size must be 256 bytes\n")
            return false
        }
        if wavfile.nchannels != 1 and wavfile.nchannels != 2 {
            txt.print("error: only mono and stereo ADPCM are supported\n")
            return false
        }
        if wavfile.data_offset + wavfile.data_size > file_size {
            txt.print("error: ADPCM data extends past end of file\n")
            return false
        }

        long blocks = wavfile.data_size / ADPCM_BLOCK_SIZE as long
        long remainder = wavfile.data_size % ADPCM_BLOCK_SIZE as long
        if remainder != 0 {
            ; the decoder only handles full blocks; drop the partial final block
            txt.print("note: skipping partial final block of ")
            txt.print_l(remainder)
            txt.print(" bytes\n")
        }
        if wavfile.nchannels == 1
            decoded_pcm_size = blocks * 1010 as long
        else
            decoded_pcm_size = blocks * 996 as long

        txt.print("decoding ")
        txt.print_l(blocks)
        txt.print(" ADPCM blocks (")
        txt.print_l(decoded_pcm_size)
        txt.print(" bytes of PCM output)...\n")

        pointer inputptr = wav_buffer + wavfile.data_offset
        pointer outputptr = &decoded_block
        if wavfile.nchannels == 1 {
            repeat blocks {
                adpcm.decode_block_mono(inputptr, outputptr)
                inputptr += ADPCM_BLOCK_SIZE
            }
        } else {
            repeat blocks {
                adpcm.decode_block_stereo(inputptr, outputptr)
                inputptr += ADPCM_BLOCK_SIZE
            }
        }
        txt.print("done\n")
        return true
    }

    sub duration_us(long start_s, long start_us, long end_s, long end_us) -> long {
        long secs = end_s - start_s
        long micro = end_us - start_us
        if micro < 0 {
            secs -= 1
            micro += 1000000
        }
        return secs * 1000000 + micro
    }

    sub print_duration(long total_us) {
        long secs = total_us / 1000000
        long micro = total_us % 1000000
        txt.print_l(secs)
        txt.print(".")
        if micro < 10
            txt.print("00000")
        else if micro < 100
            txt.print("0000")
        else if micro < 1000
            txt.print("000")
        else if micro < 10000
            txt.print("00")
        else if micro < 100000
            txt.print("0")
        txt.print_l(micro)
        txt.print(" sec")
    }

    sub print_throughput(long bytes, long total_us) {
        if total_us <= 0 {
            txt.print("N/A KB/s")
            return
        }
        long kb = bytes / 1024
        long kb_per_sec = kb * 1000000 / total_us
        txt.print_l(kb_per_sec)
        txt.print(" KB/s")
    }

    sub print_sample_rate(long bytes, long total_us) {
        ; A frame is one sample for all channels together (stereo L+R = 1 frame).
        if total_us <= 0 {
            txt.print("N/A frames/sec")
            return
        }
        long frames = bytes / (wavfile.nchannels * 2 as long)
        if total_us < 1000 {
            txt.print("> ")
            txt.print_l(frames * 1000)
            txt.print(" frames/sec")
        } else {
            long frames_per_sec = frames * 1000 / (total_us / 1000)
            txt.print_l(frames_per_sec)
            txt.print(" frames/sec")
        }
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
