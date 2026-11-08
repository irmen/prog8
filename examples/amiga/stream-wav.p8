; Streaming WAV player for the Amiga. Asks for a wav file to play, streams it
; from disk (requires a harddisk) and plays it in real time.
;
; Features:
; - plays 8-bit PCM (mono/stereo), 16-bit PCM (mono/stereo) and 16-bit
;   IMA-ADPCM (mono/stereo, 256-byte blocks) WAV files
; - 8-bit PCM plays through audio.device; 16-bit PCM and ADPCM play through
;   the sound library's 14-bit Paula playback (ADPCM is decoded to 16-bit
;   on the fly first)
; - sample rate, channel layout and data location come from the WAV header
;   itself (parsed with the wavfile library, extra chunks such as LIST
;   are skipped)
; - stereo plays left on channel 0 and right on channel 1; mono plays
;   dual-mono centered (pair 0+3 for 14-bit)
; - gapless playback: the audio.device path chains chunks via its 2-deep
;   write queue, the 14-bit path arms each chunk into Paula's reload
;   registers for seamless pickup at the buffer end
; - only 3 chunk buffer sets in chip ram (~110 KB total), no matter how
;   large the file is; the read loop is paced by the audio itself
; - prints the file properties and playback configuration before playing,
;   shows a dot per chunk while playing, ctrl+C stops and exits
; - disables the Amiga lowpass filter for crisp audio
; Warning: the 14-bit path writes Paula registers directly without
; custom.grab_system(), because it must keep DOS working for disk reads.
; Do not run it alongside another user of audio.device or Paula.

%import dos
%import audio
%import sound
%import custom
%import exec
%import textio
%import wavfile
%import adpcm

main {
    const uword FRAMES_PER_CHUNK = 5632   ; PCM sample frames per chunk (mono or stereo)
    const uword RAW_BUF_SIZE = FRAMES_PER_CHUNK*4   ; max raw bytes per disk read (16-bit stereo)
    const uword RAW14_MONO = FRAMES_PER_CHUNK*2     ; raw bytes per 14-bit chunk, mono
    const uword RAW14_STEREO = FRAMES_PER_CHUNK*4   ; raw bytes per 14-bit chunk, stereo
    const uword HEADER_BYTES = 512        ; enough to hold the fmt chunk and the data chunk header
    const uword ADPCM_BLOCK = 256         ; the decoder only handles 256-byte IMA-ADPCM blocks
    const ubyte ADPCM_BLOCKS_STEREO = 22  ; 22 blocks decode to 22*249 = 5478 frames
    const ubyte ADPCM_BLOCKS_MONO = 11    ; 11 blocks decode to 11*505 = 5555 frames
    const ubyte VOLUME = 64
    const ubyte NUM_SETS = 3         ; audio.device queue depth (2) + 1, see header

    ^^byte[NUM_SETS] left            ; rotation: set index -> chip ram sample buffer
    ^^byte[NUM_SETS] right
    ^^byte[NUM_SETS] lleft           ; low bytes for the 14-bit path (high bytes reuse left/right)
    ^^byte[NUM_SETS] lright
    bool stopped

    sub start() {
        stopped = false
        left[0] = &sounddata.left0
        left[1] = &sounddata.left1
        left[2] = &sounddata.left2
        right[0] = &sounddata.right0
        right[1] = &sounddata.right1
        right[2] = &sounddata.right2
        lleft[0] = &sounddata.lo_left0
        lleft[1] = &sounddata.lo_left1
        lleft[2] = &sounddata.lo_left2
        lright[0] = &sounddata.lo_right0
        lright[1] = &sounddata.lo_right1
        lright[2] = &sounddata.lo_right2

        txt.print("streams a wav file from disk (requires a harddisk)\n")
        str @shared filename = "?" * 60
        txt.print("wav file to stream (e.g. thriller.wav): ")
        void txt.input_chars(filename)

        pointer file = dos.Open(filename, dos.MODE_OLDFILE)
        if file==0 {
            txt.print("cannot open\n")
            sys.exit(1)
        }
        defer void dos.Close(file)

        ; read the wav header and locate the sample data (skips LIST and other extra chunks)
        long n = dos.Read(file, &sounddata.raw, HEADER_BYTES as long)
        if n!=(HEADER_BYTES as long) or not wavfile.parse_header(&sounddata.raw) {
            txt.print("not a valid wav file\n")
            sys.exit(1)
        }
        if wavfile.wavefmt!=wavfile.Format::PCM and wavfile.wavefmt!=wavfile.Format::DVI_ADPCM or wavfile.nchannels<1 or wavfile.nchannels>2 {
            txt.print("need PCM or IMA-ADPCM mono/stereo\n")
            sys.exit(1)
        }
        bool is_adpcm = wavfile.wavefmt==wavfile.Format::DVI_ADPCM
        if is_adpcm {
            if wavfile.block_align!=ADPCM_BLOCK {
                txt.print("ADPCM block size must be 256 bytes\n")
                sys.exit(1)
            }
        } else {
            if wavfile.bits_per_sample!=8 and wavfile.bits_per_sample!=16 {
                txt.print("need 8/16-bit PCM\n")
                sys.exit(1)
            }
        }
        void dos.Seek(file, wavfile.data_offset, dos.OFFSET_BEGINNING)
        bool use14 = is_adpcm or wavfile.bits_per_sample==16

        txt.print_uw(wavfile.sample_rate)
        txt.print(" Hz ")
        if wavfile.nchannels==2 {
            txt.print("stereo ")
        } else {
            txt.print("mono ")
        }
        if is_adpcm {
            txt.print("IMA-ADPCM, ")
            txt.print_uw(wavfile.block_align)
            txt.print(" byte blocks, ")
        } else {
            txt.print_ub(wavfile.bits_per_sample)
            txt.print("-bit PCM, ")
        }
        txt.print_l(wavfile.data_size)
        txt.print(" bytes, about ")
        long seconds
        if is_adpcm {
            long blocks = wavfile.data_size/ADPCM_BLOCK
            uword frames_per_block = 249
            if wavfile.nchannels==1 {
                frames_per_block = 505
            }
            seconds = (blocks*(frames_per_block as long))/(wavfile.sample_rate as long)
        } else {
            seconds = wavfile.data_size/(((wavfile.sample_rate as long)*wavfile.nchannels)*(wavfile.bits_per_sample/8))
        }
        txt.print_l(seconds)
        txt.print(" sec (ctrl+C to stop)\n")
        if use14 {
            txt.print("playback: 14-bit Paula via sound library, channels ")
            if wavfile.nchannels==2 {
                txt.print("0+3 left, 1+2 right\n")
            } else {
                txt.print("0+3 mono\n")
            }
        } else {
            if not audio.init() {
                txt.print("cannot open audio.device\n")
                sys.exit(1)
            }
            defer audio.closedown()
            txt.print("playback: 8-bit via audio.device, channels 0+1\n")
        }
        custom.filter_off()     ; disable the muffled lowpass filter for crisp audio

        long total_frames
        if use14 {
            total_frames = stream_14bit(file, is_adpcm)
        } else {
            total_frames = stream_pcm(file)
        }

        txt.nl()
        if not use14 {
            if stopped
                audio.stop_all()
            audio.wait_all()
        }
        if stopped {
            txt.print("\nStopped, played ")
        } else {
            txt.print("\ndone, played ")
        }
        txt.print_l(total_frames)
        txt.print(" frames (")
        txt.print_l(total_frames/(wavfile.sample_rate as long))
        txt.print(" sec)\n")
    }

    sub stream_pcm(pointer file) -> long {
        ubyte frame_bytes = wavfile.nchannels * (wavfile.bits_per_sample/8)
        ; nb: plain FRAMES_PER_CHUNK*frame_bytes multiplies by the constant
        ; 5632, which the m68k backend decomposes into lsl #9 (illegal on 68000)
        uword raw_chunk = FRAMES_PER_CHUNK
        when frame_bytes {
            2 -> raw_chunk = FRAMES_PER_CHUNK*2
            4 -> raw_chunk = FRAMES_PER_CHUNK*4
        }

        long total_frames = 0
        long remaining = wavfile.data_size
        long n
        ubyte set = 0
        ; read and queue one chunk per pass, rotating through the buffer sets.
        ; play_queued blocks (by reaping the oldest write) only when the 2-deep device queue
        ; is full, so the loop is naturally paced by the audio and never overruns the buffers.
        do {
            long want = raw_chunk as long
            if want>remaining
                want = remaining
            n = dos.Read(file, &sounddata.raw, want)
            if n>0 {
                uword frames = (n/(frame_bytes as long)) as uword
                if frames>=2 {
                    txt.chrout('.')
                    txt.flush()
                    prepare_chunk(set, frames)
                    total_frames += frames as long
                    queue_chunk(set, frames)
                    set = (set+1) % NUM_SETS
                }
                remaining -= n
            }
        } until n<=0 or remaining<=0 or ctrl_c_pressed()
        if n<0
            txt.print("\nread error!\n")
        return total_frames
    }

    sub ctrl_c_pressed() -> bool {
        bool pressed = (exec.SetSignal(0, dos.SIGBREAKF_CTRL_C) & dos.SIGBREAKF_CTRL_C) != 0
        if pressed
            stopped = true
        return pressed
    }

    sub stream_14bit(pointer file, bool is_adpcm) -> long {
        ; gapless 14-bit stereo/mono streaming straight to Paula via the sound
        ; library: each chunk is split into high bytes (full volume) and low
        ; bytes (volume 1) on a paired channel, started looped, and the next
        ; chunk is armed into Paula's reload registers (AUDxPT/AUDxLEN), which
        ; pick it up seamlessly when the current buffer ends. The buffer end
        ; is detected by polling channel 0's audio interrupt request flag;
        ; servicing it any time within one chunk (~0.35s) keeps playback gapless.
        sound.init()
        defer sound.closedown()
        uword period = (sound.CLOCK_PAL/(wavfile.sample_rate as long)) as uword
        long total_frames = 0
        long remaining = wavfile.data_size
        ubyte set = 0
        uword frames
        long got
        frames, got = fill_14bit_chunk(file, set, remaining, is_adpcm)
        if frames<2
            return 0
        remaining -= got
        pad_14bit_chunk(set, frames)
        clear_audio_flags()
        start_14bit(set, (frames+1)/2, period)
        total_frames += frames as long
        set = 1
        frames, got = fill_14bit_chunk(file, set, remaining, is_adpcm)
        if frames<2 {
            ; a single chunk in total: let it play out, then fall silent
            end_14bit_playback(false)
            return total_frames
        }
        remaining -= got
        pad_14bit_chunk(set, frames)
        arm_14bit(set, (frames+1)/2)
        total_frames += frames as long
        ubyte free = 2
        while remaining>0 {
            frames, got = fill_14bit_chunk(file, free, remaining, is_adpcm)
            if frames<2
                break
            remaining -= got
            pad_14bit_chunk(free, frames)
            if not wait_audio_flag() {
                sound.stop_all()
                return total_frames
            }
            arm_14bit(free, (frames+1)/2)
            total_frames += frames as long
            free = (free+1) % NUM_SETS
        }
        ; no more data: the armed chunk plays out, then silence
        end_14bit_playback(true)
        return total_frames
    }

    sub fill_14bit_chunk(pointer file, ubyte set, long remaining, bool is_adpcm) -> uword, long {
        if is_adpcm {
            return fill_adpcm14_chunk(file, set, remaining)
        }
        ; read one chunk of 16-bit PCM frames and split it into high/low byte
        ; channel buffers (high bytes reuse the left/right buffers)
        uword fbytes = (wavfile.nchannels as uword)*2
        long want = RAW14_MONO as long
        if fbytes==4 {
            want = RAW14_STEREO as long
        }
        if want>remaining
            want = remaining
        if want<=0
            return 0, 0
        long n = dos.Read(file, &sounddata.raw, want)
        if n<=0
            return 0, 0
        uword frames = (n/(fbytes as long)) as uword
        if frames<2
            return 0, n
        split_16bit_samples(&sounddata.raw, set, frames, false)
        txt.chrout('.')
        txt.flush()
        return frames, n
    }

    sub fill_adpcm14_chunk(pointer file, ubyte set, long remaining) -> uword, long {
        ; read ADPCM blocks, decode them to 16-bit and split into high/low
        ; byte channel buffers (high bytes reuse the left/right buffers)
        ubyte blocks_per_chunk = ADPCM_BLOCKS_STEREO
        uword frames_per_block = 249
        if wavfile.nchannels==1 {
            blocks_per_chunk = ADPCM_BLOCKS_MONO
            frames_per_block = 505
        }
        long maxblocks = remaining/ADPCM_BLOCK
        ubyte nblocks = blocks_per_chunk
        if (nblocks as long)>maxblocks
            nblocks = maxblocks as ubyte
        if nblocks==0
            return 0, 0
        long want = ((nblocks as uword)*ADPCM_BLOCK) as long
        ; accumulate full reads: a short read here would misalign the blocks
        pointer rp = &sounddata.raw
        long got = 0
        long n
        bool readerr = false
        do {
            n = dos.Read(file, rp, want-got)
            if n<0
                readerr = true
            if n>0 {
                rp += n
                got += n
            }
        } until n<=0 or got>=want
        if got!=want {
            if readerr
                txt.print("\nread error!\n")
            return 0, 0
        }
        pointer src = &sounddata.raw
        pointer dst = &sounddata.decoded
        ubyte b = 0
        while b<nblocks {
            if wavfile.nchannels==2 {
                adpcm.decode_block_stereo(src, dst)
                dst += 996
            } else {
                adpcm.decode_block_mono(src, dst)
                dst += 1010
            }
            src += ADPCM_BLOCK
            b++
        }
        uword frames = (nblocks as uword)*frames_per_block
        split_16bit_samples(&sounddata.decoded, set, frames, true)
        txt.chrout('.')
        txt.flush()
        return frames, got
    }

    sub split_16bit_samples(pointer src, ubyte set, uword frames, bool big_endian) {
        uword high_offset = 1
        if big_endian
            high_offset = 0
        uword low_offset = high_offset ^ 1
        if wavfile.nchannels==2 {
            ^^byte hi_l = left[set]
            ^^byte lo_l = lleft[set]
            ^^byte hi_r = right[set]
            ^^byte lo_r = lright[set]
            for i in 0 to frames-1 {
                hi_l[i] = src[i*4+high_offset] as byte
                lo_l[i] = src[i*4+low_offset] as byte
                hi_r[i] = src[i*4+2+high_offset] as byte
                lo_r[i] = src[i*4+2+low_offset] as byte
            }
        } else {
            ^^byte hi = left[set]
            ^^byte lo = lleft[set]
            for i in 0 to frames-1 {
                hi[i] = src[i*2+high_offset] as byte
                lo[i] = src[i*2+low_offset] as byte
            }
        }
    }

    sub start_14bit(ubyte set, uword words, uword period) {
        ; start a chunk looped on the 14-bit pair(s): high bytes at full
        ; volume, low bytes at volume 1 (the classic 14-bit Paula technique)
        sound.loop(0, left[set], words, period, VOLUME)
        sound.loop(3, lleft[set], words, period, 1)
        if wavfile.nchannels==2 {
            sound.loop(1, right[set], words, period, VOLUME)
            sound.loop(2, lright[set], words, period, 1)
        }
    }

    sub arm_14bit(ubyte set, uword words) {
        ; arm the next chunk into Paula's reload registers; it is picked up
        ; seamlessly when the current buffer ends (no DMA restart needed)
        custom.AUD0PT = left[set]
        custom.AUD0LEN = words
        custom.AUD3PT = lleft[set]
        custom.AUD3LEN = words
        if wavfile.nchannels==2 {
            custom.AUD1PT = right[set]
            custom.AUD1LEN = words
            custom.AUD2PT = lright[set]
            custom.AUD2LEN = words
        }
    }

    sub arm_silence() {
        custom.AUD0PT = &sounddata.silword
        custom.AUD0LEN = 1
        custom.AUD3PT = &sounddata.silword
        custom.AUD3LEN = 1
        if wavfile.nchannels==2 {
            custom.AUD1PT = &sounddata.silword
            custom.AUD1LEN = 1
            custom.AUD2PT = &sounddata.silword
            custom.AUD2LEN = 1
        }
    }

    sub clear_audio_flags() {
        custom.INTREQ = custom.INTF_AUD0 | custom.INTF_AUD1 | custom.INTF_AUD2 | custom.INTF_AUD3
    }

    sub wait_audio_flag() -> bool {
        ; wait until the current Paula buffer ends (channel 0 flag); the other
        ; channels run the same length/period and end with it. False on ctrl+C.
        while (custom.INTREQR & custom.INTF_AUD0)==0 {
            if ctrl_c_pressed()
                return false
            sys.waitvsync()
        }
        clear_audio_flags()
        return true
    }

    sub end_14bit_playback(bool queued_chunk) {
        ; Let an already queued chunk start before replacing its reload with silence.
        if queued_chunk and not wait_audio_flag() {
            sound.stop_all()
            return
        }
        arm_silence()
        if not wait_audio_flag() {
            sound.stop_all()
            return
        }
        sound.stop_all()
    }

    sub pad_14bit_chunk(ubyte set, uword frames) {
        if (frames & 1)==0
            return
        uword last = frames-1
        ^^byte hi_l = left[set]
        ^^byte lo_l = lleft[set]
        hi_l[frames] = hi_l[last]
        lo_l[frames] = lo_l[last]
        if wavfile.nchannels==2 {
            ^^byte hi_r = right[set]
            ^^byte lo_r = lright[set]
            hi_r[frames] = hi_r[last]
            lo_r[frames] = lo_r[last]
        }
    }

    sub queue_chunk(ubyte set, uword frames) {
        audio.play_queued(0, left[set], frames as long, wavfile.sample_rate, VOLUME, 1)
        if wavfile.nchannels==2 {
            audio.play_queued(1, right[set], frames as long, wavfile.sample_rate, VOLUME, 1)
        } else {
            audio.play_queued(1, left[set], frames as long, wavfile.sample_rate, VOLUME, 1)
        }
    }

    sub prepare_chunk(ubyte set, uword frames) {
        ; convert one raw chunk of 8/16-bit PCM sample frames into the set's
        ; channel buffer(s). 16-bit WAV samples are already signed, so only the
        ; high byte is kept; 8-bit WAV samples are unsigned (silence = $80)
        ; and are sign-flipped via the wavfile helper.
        if wavfile.nchannels==2 {
            if wavfile.bits_per_sample==16 {
                ^^byte dst_left = left[set]
                ^^byte dst_right = right[set]
                for i in 0 to frames-1 {
                    dst_left[i] = sounddata.raw[i*4+1] as byte
                    dst_right[i] = sounddata.raw[i*4+3] as byte
                }
            } else {
                wavfile.convert_8bit_stereo(&sounddata.raw, left[set], right[set], frames as long)
            }
        } else {
            ^^byte dst = left[set]
            if wavfile.bits_per_sample==16 {
                for i in 0 to frames-1
                    dst[i] = sounddata.raw[i*2+1] as byte
            } else {
                for i in 0 to frames-1
                    dst[i] = (sounddata.raw[i] ^ $80) as byte
            }
        }
    }
}

sounddata {
    %option amiga_chipram     ; the audio DMA can only read from chip ram

    ubyte[main.RAW_BUF_SIZE] raw
    ubyte[main.ADPCM_BLOCKS_STEREO*996] decoded
    byte[main.FRAMES_PER_CHUNK] left0
    byte[main.FRAMES_PER_CHUNK] left1
    byte[main.FRAMES_PER_CHUNK] left2
    byte[main.FRAMES_PER_CHUNK] right0
    byte[main.FRAMES_PER_CHUNK] right1
    byte[main.FRAMES_PER_CHUNK] right2
    byte[main.FRAMES_PER_CHUNK] lo_left0
    byte[main.FRAMES_PER_CHUNK] lo_left1
    byte[main.FRAMES_PER_CHUNK] lo_left2
    byte[main.FRAMES_PER_CHUNK] lo_right0
    byte[main.FRAMES_PER_CHUNK] lo_right1
    byte[main.FRAMES_PER_CHUNK] lo_right2
    uword silword     ; silent reload word for the end of 14-bit playback
}
