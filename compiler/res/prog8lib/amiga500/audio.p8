%import exec
%import syslib

audio {
    %option no_symbol_prefixing, ignore_unused

    ^^IOAudio @shared AudioIO

    ; audio.device IO commands:
    const uword ADCMD_FREE = 9
    const uword ADCMD_SETPREC = 10
    const uword ADCMD_FINISH = 11
    const uword ADCMD_PERVOL = 12
    const uword ADCMD_LOCK = 13
    const uword ADCMD_WAITCYCLE = 14
    const uword ADCMD_ALLOCATE = 32

    ; IOAudio flags (explicit ubyte values):
    const ubyte ADIOB_PERVOL = 4
    const ubyte ADIOF_PERVOL = $10
    const ubyte ADIOB_SYNCCYCLE = 5
    const ubyte ADIOF_SYNCCYCLE = $20
    const ubyte ADIOB_NOWAIT = 6
    const ubyte ADIOF_NOWAIT = $40
    const ubyte ADIOB_WRITEMESSAGE = 7
    const ubyte ADIOF_WRITEMESSAGE = $80

    ; IOAudio error codes:
    const byte ADIOERR_NOALLOCATION = -10
    const byte ADIOERR_ALLOCFAILED = -11
    const byte ADIOERR_CHANNELSTOLEN = -12

    ; hardware constants:
    const uword ADHARD_CHANNELS = 4
    const byte ADALLOC_MINPREC = -128
    const byte ADALLOC_MAXPREC = 127

    struct IOAudio {
        ; struct IORequest (base, 32 bytes)
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        pointer ReplyPort  ; 14
        uword Length  ; 18
        pointer Device  ; 20
        pointer Unit  ; 24
        uword Command  ; 28
        ubyte Flags  ; 30
        byte Error  ; 31
        ; struct IOAudio specific fields
        word AllocKey  ; 32
        pointer Data  ; 34
        long IOAudio_Length  ; 38
        uword Period  ; 42
        uword Volume  ; 44
        uword Cycles  ; 46
        ; struct Message ioa_WriteMsg (20 bytes)
        pointer Msg_Succ  ; 48
        pointer Msg_Pred  ; 52
        ubyte Msg_Type  ; 56
        byte Msg_Pri  ; 57
        str Msg_Name  ; 58
        pointer Msg_ReplyPort  ; 62
        uword Msg_Length  ; 66
    }

    sub opendevice(pointer channelPrefs, ubyte prefsLen, byte precedence) -> bool {
        ; Allocate and initialize MsgPort manually (Kickstart 1.3 compatible)
        ^^exec.MsgPort audioPort = exec.AllocMem(sizeof(exec.MsgPort), exec.MEMF_PUBLIC | exec.MEMF_CLEAR)
        if audioPort == 0 return false

        AudioIO = exec.AllocMem(sizeof(audio.IOAudio), exec.MEMF_PUBLIC | exec.MEMF_CLEAR)
        if AudioIO == 0 {
            exec.FreeMem(audioPort, sizeof(exec.MsgPort))
            return false
        }

        audioPort.Type = exec.NT_MSGPORT
        audioPort.Flags = exec.PA_SIGNAL
        audioPort.SigBit = exec.AllocSignal(-1) as ubyte
        if audioPort.SigBit==255
            return false
        audioPort.SigTask = exec.FindTask(0)
        ^^exec.List listPtr = &&audioPort.Head as ^^exec.List
        exec.NewList(listPtr)
        AudioIO.ReplyPort = audioPort

        ; Set channel allocation preferences before opening
        AudioIO.Pri = precedence
        AudioIO.AllocKey = 0
        AudioIO.Data = channelPrefs
        AudioIO.IOAudio_Length = prefsLen

        if exec.OpenDevice("audio.device", 0, AudioIO, 0)==0
            return true

        exec.FreeSignal(audioPort.SigBit as byte)
        exec.FreeMem(AudioIO, sizeof(audio.IOAudio))
        exec.FreeMem(audioPort, sizeof(exec.MsgPort))
        AudioIO = 0
        return false
    }

    sub closedevice() {
        if AudioIO != 0 {
            ^^exec.MsgPort audioPort = AudioIO.ReplyPort
            byte sigbit = audioPort.SigBit as byte
            exec.CloseDevice(AudioIO)
            exec.FreeSignal(sigbit)
            exec.FreeMem(AudioIO, sizeof(audio.IOAudio))
            exec.FreeMem(audioPort, sizeof(exec.MsgPort))
            AudioIO = 0
        }
    }

    sub period(uword samplerate) -> uword {
        ; calculate the Period to use for the desired sample rate
        return audio.clock() / samplerate as uword
    }

    asmsub clock() clobbers (A6) -> long @D0 {
        ; return the Audio/Color clock, used for Period calculations to get the correct frequency
        %asm {{
            move.l  sys.GfxBase, a6
            btst.b  #0, $dc(a6)
            bne.s   .ntsc
            move.l  #3546895, d0        ; PAL
            rts
.ntsc:
            move.l  #3579545, d0        ; NTSC
            rts
        }}
    }

    asmsub BeginIO(^^IOAudio io @A1) clobbers (D0, D1, A0, A1, A6) {
        ; Calls the device's BeginIO vector directly, instead of exec DoIO/SendIO,
        ; because those clear io_Flags which would wipe ADIOF_PERVOL and friends.
        %asm {{
            move.l  20(a1), a6          ; a6 = io_Device (offset 20 in IORequest)
            jsr     -30(a6)             ; device BeginIO vector
            rts
        }}
    }
}
