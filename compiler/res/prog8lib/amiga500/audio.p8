%import exec

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

    sub opendevice(pointer channelPrefs, uword numChannels, byte precedence) -> bool {
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
        audioPort.SigTask = exec.FindTask(0)
        ^^exec.List listPtr = &&audioPort.Head as ^^exec.List
        exec.NewList(listPtr)
        AudioIO.ReplyPort = audioPort

        ; Set channel allocation preferences before opening (explicit long cast)
        AudioIO.Pri = precedence
        AudioIO.AllocKey = 0
        AudioIO.Data = channelPrefs
        AudioIO.IOAudio_Length = numChannels as long

        if exec.OpenDevice("audio.device", 0, AudioIO, 0)==0 {
            return true
        }

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
}
