%import exec

ahi {
    %option no_symbol_prefixing, ignore_unused

    ; Playback-only binding for ahi.device, high-level (CMD_WRITE) API.
    ; AHI v4 compatible: ahir_Version is 4 and only 8/16-bit sample types
    ; are exposed (32-bit types need version 6+).
    ; The unit to play on is selectable via ahi.open() (pass 0 = AHI_DEFAULT_UNIT
    ; for the user's default output, or 1-3 for another configured unit; AHI_NO_UNIT
    ; only makes sense for low-level-only access and is not supported here).
    ; The low-level control and audio-mode query LVOs (audio channel handles)
    ; are exposed as extsubs below; their hooks and requester are deliberately
    ; not covered.
    ; Requires a system with ahi.device installed (real hardware or full
    ; emulation); not testable under vamos. Amiga1200 target only (68020+,
    ; Kickstart 3+): uses CreateMsgPort/CreateIORequest for port handling.
    ; Struct offsets verified against NDK3.2 exec/io.h (IOStdReq) and the
    ; AHI v6 dev kit (Developer/Include/Asm/devices/ahi.i, AHIRequest).

    ; ---- constants ----

    const uword AHI_VERSION = 4     ; ahir_Version preset before OpenDevice

    const ubyte AHI_DEFAULT_UNIT = 0
    const ubyte AHI_NO_UNIT = 255    ; low-level API only, no hardware allocated

    const long AHI_INVALID_ID = $ffffffff    ; audio id sentinel, not a valid mode
    const long AHI_DEFAULT_ID = 0            ; AllocAudioA: use the default mode

    ; sample formats for ahir_Type (all V4-compatible)
    const long AHIST_M8S = 0        ; mono, 8-bit signed
    const long AHIST_M16S = 1       ; mono, 16-bit signed (big-endian WORDs)
    const long AHIST_S8S = 2        ; stereo, 8-bit signed
    const long AHIST_S16S = 3       ; stereo, 16-bit signed (big-endian WORDs)

    ; volume and stereo position are 16.16 Fixed
    const long FULL_VOLUME = $10000
    const long PAN_LEFT = 0
    const long PAN_CENTER = $8000
    const long PAN_RIGHT = $10000

    ; error codes (AHIE_*, seen in io_Error alongside exec IO errors)
    const byte AHIE_OK = 0
    const byte AHIE_NOMEM = 1
    const byte AHIE_BADSOUNDTYPE = 2
    const byte AHIE_BADSAMPLETYPE = 3
    const byte AHIE_ABORTED = 4
    const byte AHIE_UNKNOWN = 5
    const byte AHIE_HALFDUPLEX = 6

    ; ---- low-level LVO API ----
    ; These call the device's library-style routines through its base
    ; (sys.AHIBase, @bank 101), which ahi.open() sets from the opened
    ; device's io_Device. Register assignments and offsets verified against
    ; the AHI dev kit ahi_lib.fd/sfd (bias 42). The synth routines need an
    ; AudioCtrl handle from ahi.AllocAudioA(); the query trio reads the
    ; audio-mode database, useful for diagnostics. Taglists are plain
    ; 'pointer' parameters (like the intuition bindings): build them as
    ; ^^utility.TagItem arrays of {tag, data} pairs, terminated by
    ; utility.TAG_END. Data is a pointer-or-value per the tag's semantics.

    extsub @bank 101  -42 = AllocAudioA(pointer tagList @A1) -> pointer @D0  ; returns AudioCtrl, or 0 (AF_NULL) if failed
    extsub @bank 101  -48 = FreeAudio(pointer audioCtrl @A2)
    extsub @bank 101  -60 = ControlAudioA(pointer audioCtrl @A2, pointer tagList @A1) -> long @D0
    extsub @bank 101  -66 = SetVol(uword channel @D0, long volume @D1, long pan @D2, pointer audioCtrl @A2, long flags @D3)
    extsub @bank 101  -72 = SetFreq(uword channel @D0, long freq @D1, pointer audioCtrl @A2, long flags @D2)
    extsub @bank 101  -78 = SetSound(uword channel @D0, uword sound @D1, long offset @D2, long length @D3, pointer audioCtrl @A2, long flags @D4)
    extsub @bank 101  -90 = LoadSound(uword sound @D0, long sampletype @D1, pointer info @A0, pointer audioCtrl @A2) -> long @D0
    extsub @bank 101  -96 = UnloadSound(uword sound @D0, pointer audioCtrl @A2)
    extsub @bank 101 -102 = NextAudioID(long lastID @D0) -> long @D0      ; next id, or AHI_INVALID_ID at the end
    extsub @bank 101 -108 = GetAudioAttrsA(long id @D0, pointer audioCtrl @A2, pointer tagList @A1) -> long @D0    ; 1=ok, 0=invalid id
    extsub @bank 101 -114 = BestAudioIDA(pointer tagList @A1) -> long @D0     ; best matching audio id, AHI_INVALID_ID if none

    ; For ahi.BestAudioIDA(): hard requirements go in the main taglist, and
    ; AHIB_Dizzy points at a second taglist of soft preferences - after the
    ; hard ones are applied, the mode with the most of the wanted (and fewest
    ; of the unwanted) features wins. Without it the call hardly does what its
    ; name suggests, so it is worth passing.
    const long AHIB_Dizzy = $800000be

    ; ---- low-level tag constants ----
    ; AHIC_* go to ahi.ControlAudioA(), AHIDB_* to ahi.GetAudioAttrsA(),
    ; AHIB_* to ahi.BestAudioIDA().
    ; Boolean AHIDB_* tags return 0/1; the R-bit string tags (driver, name,
    ; author, copyright, version, annotation) are OUT parameters: the device
    ; writes a string into the buffer you pass (size it via AHIDB_BufferLen).
    ; Tag base is utility.TAG_USER.
    const long AHIC_Play = $80000050
    const long AHIC_Record = $80000051
    const long AHIC_MonitorVolume = $80000052
    const long AHIC_MonitorVolume_Query = $80000053
    const long AHIC_MixFreq_Query = $80000054
    const long AHIC_InputGain = $80000055
    const long AHIC_InputGain_Query = $80000056
    const long AHIC_OutputVolume = $80000057
    const long AHIC_OutputVolume_Query = $80000058
    const long AHIC_Input = $80000059
    const long AHIC_Input_Query = $8000005a
    const long AHIC_Output = $8000005b
    const long AHIC_Output_Query = $8000005c

    const long AHIDB_AudioID = $80000064
    const long AHIDB_Driver = $80008065     ; R
    const long AHIDB_Volume = $80000067
    const long AHIDB_Panning = $80000068
    const long AHIDB_Stereo = $80000069
    const long AHIDB_HiFi = $8000006a
    const long AHIDB_PingPong = $8000006b
    const long AHIDB_Name = $8000806d       ; R
    const long AHIDB_Bits = $8000006e
    const long AHIDB_MaxChannels = $8000006f
    const long AHIDB_MinMixFreq = $80000070
    const long AHIDB_MaxMixFreq = $80000071
    const long AHIDB_Record = $80000072
    const long AHIDB_Frequencies = $80000073
    const long AHIDB_FrequencyArg = $80000074
    const long AHIDB_Frequency = $80000075
    const long AHIDB_Author = $80008076      ; R
    const long AHIDB_Copyright = $80008077   ; R
    const long AHIDB_Version = $80008078     ; R
    const long AHIDB_Annotation = $80008079  ; R
    const long AHIDB_BufferLen = $8000007a
    const long AHIDB_IndexArg = $8000007b
    const long AHIDB_Index = $8000007c
    const long AHIDB_Realtime = $8000007d
    const long AHIDB_MaxPlaySamples = $8000007e
    const long AHIDB_MaxRecordSamples = $8000007f
    const long AHIDB_FullDuplex = $80000081
    const long AHIDB_MinMonitorVolume = $80000082
    const long AHIDB_MaxMonitorVolume = $80000083
    const long AHIDB_MinInputGain = $80000084
    const long AHIDB_MaxInputGain = $80000085
    const long AHIDB_MinOutputVolume = $80000086
    const long AHIDB_MaxOutputVolume = $80000087
    const long AHIDB_Inputs = $80000088
    const long AHIDB_InputArg = $80000089
    const long AHIDB_Input = $8000008a
    const long AHIDB_Outputs = $8000008b
    const long AHIDB_OutputArg = $8000008c
    const long AHIDB_Output = $8000008d

    ; ---- struct ----

    struct AHIRequest {             ; total size: 80
        ; struct IOStdReq base (48 bytes, same layout as exec.IOStdReq)
        ^^exec.Node Succ  ; 0
        ^^exec.Node Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        ^^exec.MsgPort ReplyPort  ; 14
        uword Length  ; 18
        pointer Device  ; 20
        long Unit  ; 24
        uword Command  ; 28
        ubyte Flags  ; 30
        byte Error  ; 31
        long Actual  ; 32
        long IOStdReq_Length  ; 36
        pointer Data  ; 40
        long Offset  ; 44
        ; AHI extension
        uword Version  ; 48
        uword Pad  ; 50
        long Private1  ; 52
        long Private2  ; 56
        long SampleType  ; 60
        long Frequency  ; 64
        long Volume  ; 68
        long Position  ; 72
        pointer Link  ; 76
    }

    ; Two request blocks for gapless double-buffered playback, used
    ; ping-pong fashion and chained via ahir_Link. Allocated with
    ; CreateIORequest (Kickstart 2.04+), sharing a single reply port.
    ^^AHIRequest Req0
    ^^AHIRequest Req1
    private ^^exec.MsgPort port
    private ubyte queued               ; number of outstanding writes (0..2)
    private bool play_alt              ; next play uses Req1
    private bool wait_alt              ; next wait reaps Req1
    private bool opened

    ; ---- high level playback interface ----

    sub open(ubyte unit) -> bool {
        ; -- Open ahi.device for playback on the given unit (0 = default,
        ;    1-3 other configured units; see the header note above).
        ;    Returns false if unavailable.
        if opened
            return true
        port = exec.CreateMsgPort()
        if port == 0
            return false
        Req0 = exec.CreateIORequest(port, sizeof(ahi.AHIRequest))
        if Req0 == 0 {
            exec.DeleteMsgPort(port)
            return false
        }
        Req0.Version = AHI_VERSION
        if exec.OpenDevice("ahi.device", unit, Req0, 0)!=0 {
            exec.DeleteIORequest(Req0)
            exec.DeleteMsgPort(port)
            return false
        }
        Req1 = exec.CreateIORequest(port, sizeof(ahi.AHIRequest))
        if Req1 == 0 {
            exec.CloseDevice(Req0)
            exec.DeleteIORequest(Req0)
            exec.DeleteMsgPort(port)
            return false
        }
        Req1.Device = Req0.Device
        Req1.Unit = Req0.Unit
        Req1.Version = AHI_VERSION
        sys.AHIBase = Req0.Device    ; base for the low-level LVO calls above
        queued = 0
        play_alt = false
        wait_alt = false
        opened = true
        return true
    }

    sub close() {
        ; -- Abort pending audio, close the device, free the port and requests.
        if not opened
            return
        stop()
        exec.CloseDevice(Req0)
        exec.DeleteIORequest(Req0)
        exec.DeleteIORequest(Req1)
        exec.DeleteMsgPort(port)
        sys.AHIBase = 0
        opened = false
    }

    sub write_sync(pointer samples, long num_bytes, long sampletype, uword frequency, long volume, long position) -> byte {
        ; -- Play a sample synchronously, blocks until finished. Returns io_Error.
        while queued>0
            wait_oldest()
        num_bytes = setup(Req0, 0, samples, num_bytes, sampletype, frequency, volume, position)
        if num_bytes==0
            return AHIE_OK
        void exec.DoIO(Req0)
        return Req0.Error
    }

    sub play(pointer samples, long num_bytes, long sampletype, uword frequency, long volume, long position) {
        ; -- Play a sample asynchronously on the (mixed) output.
        ; If chunks were queued earlier with play_queued, those are waited for first,
        ; so this never reuses a request block that still has a write in flight.
        while queued>0
            wait_oldest()
        num_bytes = setup(Req0, 0, samples, num_bytes, sampletype, frequency, volume, position)
        if num_bytes==0
            return
        BeginIO(Req0)
        queued = 1
        play_alt = true
        wait_alt = false
        ; sound now plays asynchronously.
    }

    sub play_queued(pointer samples, long num_bytes, long sampletype, uword frequency, long volume, long position) {
        ; -- Queue a sample chunk for gapless playback (ahir_Link chaining).
        ; Unlike play(), this may be called while the previous chunk is still playing:
        ; AHI starts it the moment the previous chunk ends.
        ; If both slots are full this waits for the oldest chunk to finish first.
        if queued==2
            wait_oldest()
        ^^AHIRequest io = Req0
        pointer link = 0
        if play_alt {
            io = Req1
            link = Req0
        } else {
            if queued>0
                link = Req1
        }
        num_bytes = setup(io, link, samples, num_bytes, sampletype, frequency, volume, position)
        if num_bytes==0
            return
        play_alt = not play_alt
        BeginIO(io)
        queued = queued+1
        ; chunk plays (or stays queued) asynchronously.
    }

    sub wait() {
        ; -- Wait for all queued audio to finish playing.
        while queued>0
            wait_oldest()
    }

    sub stop() {
        ; -- Abort pending playback. Only our own requests are aborted;
        ; CMD_FLUSH would kill other programs' audio too and must not be used here.
        while queued>0 {
            ^^AHIRequest io = get_slot(wait_alt)
            exec.AbortIO(io)
            void exec.WaitIO(io)
            wait_alt = not wait_alt
            queued = queued-1
        }
        play_alt = false
        wait_alt = false
    }

    inline sub is_playing() -> bool {
        ; -- True while chunks are queued but not yet reaped by wait().
        ; Note: a finished chunk still counts until wait() reaps it.
        return queued>0
    }

    sub frame_size(long sampletype) -> ubyte {
        ; -- Bytes per sample frame for the given AHIST_ type.
        when sampletype {
            AHIST_M8S -> return 1
            AHIST_M16S, AHIST_S8S -> return 2
            else -> return 4
        }
    }

    private sub get_slot(bool alt) -> ^^AHIRequest {
        ; request block taking the next play/wait (primary or alternate).
        if alt
            return Req1
        return Req0
    }

    private sub wait_oldest() {
        ; reap the oldest outstanding write.
        if queued==0
            return
        ^^AHIRequest io = get_slot(wait_alt)
        void exec.WaitIO(io)
        wait_alt = not wait_alt
        queued = queued-1
        if queued==0 {
            play_alt = false
            wait_alt = false
        }
    }

    private sub setup(^^AHIRequest io, pointer link, pointer samples, long num_bytes, long sampletype, uword frequency, long volume, long position) -> long {
        ; -- Fill a request block. Rounds the length down to whole sample frames
        ;    (io_Length must be a multiple of the frame size); io_Offset must be 0.
        ;    Returns the rounded length (0 = nothing to play).
        ubyte frame = frame_size(sampletype)
        num_bytes = num_bytes / frame * frame
        if num_bytes==0
            return 0
        io.Command = exec.CMD_WRITE
        io.Flags = 0
        io.Data = samples
        io.IOStdReq_Length = num_bytes
        io.Offset = 0
        io.SampleType = sampletype
        io.Frequency = frequency
        io.Volume = volume
        io.Position = position
        io.Link = link
        return num_bytes
    }

    private asmsub BeginIO(^^AHIRequest io @A1) clobbers (D0, D1, A0, A1, A6) {
        ; Calls the device's BeginIO vector directly, instead of exec DoIO/SendIO,
        ; because those clear io_Flags.
        %asm {{
            move.l  20(a1), a6          ; a6 = io_Device (offset 20 in IORequest)
            jsr     -30(a6)             ; device BeginIO vector
            rts
        }}
    }
}
