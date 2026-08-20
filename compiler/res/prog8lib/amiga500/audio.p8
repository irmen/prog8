%import exec
%import syslib

audio {
    %option no_symbol_prefixing, ignore_unused

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

    ; 4 audio channel I/O structures to address each channel individually
    ^^IOAudio AudioIO0 = []
    ^^IOAudio AudioIO1 = []
    ^^IOAudio AudioIO2 = []
    ^^IOAudio AudioIO3 = []

    private ^^exec.MsgPort msgport0 = []
    private ^^exec.MsgPort msgport1 = []
    private ^^exec.MsgPort msgport2 = []
    private ^^exec.MsgPort msgport3 = []
    private bool[4] active_channels

    ; ---- high level audio interface ----

    sub init() -> bool {
        ; -- Initialize the audio device on all 4 channels.
        ubyte[1] channel_matrix = [15]      ; allocate all 4 channels at once
        for ubyte channel in 0 to 3
            active_channels[channel] = false
        return opendevice(channel_matrix, 1, 0)
    }

    sub closedown() {
        ; -- Close down the audio device on all 4 channels. Does not wait for sounds to finish playing.
        closedevice()
        for ubyte channel in 0 to 3
            active_channels[channel] = false
    }

    sub play(ubyte channel, ^^byte samples, long num_samples, uword sample_rate, ubyte volume, uword cycles) {
        ; -- Play a sample asynchronously on the given channel (0-3), with the given parameters.
        ^^IOAudio io = get_io(channel)
        io.Command = exec.CMD_WRITE
        io.Flags = ADIOF_PERVOL
        io.Data = samples
        io.IOAudio_Length = num_samples
        io.Period = period(sample_rate)
        io.Volume = volume
        io.Cycles = cycles
        BeginIO(io)  ; not exec.DoIO/SendIO: those clear io_Flags, wiping ADIOF_PERVOL!
        active_channels[channel] = true
        ; sound now plays asynchronously.
    }

    sub wait_channel(ubyte channel) {
        ; wait for the current sound on this channel to finish playing.
        if active_channels[channel] {
            void exec.WaitIO(get_io(channel))
            active_channels[channel] = false
        }
    }

    sub wait_all() {
        ; wait for all channels to finish playing their sounds.
        wait_channel(0)
        wait_channel(1)
        wait_channel(2)
        wait_channel(3)
    }


    ; ---- low level audio interface ----

    sub opendevice(pointer channelPrefs, ubyte prefsLen, byte precedence) -> bool {
        ; Set channel allocation preferences before opening
        AudioIO0.Pri = precedence
        AudioIO0.AllocKey = 0
        AudioIO0.Data = channelPrefs
        AudioIO0.IOAudio_Length = prefsLen

        if exec.OpenDevice("audio.device", 0, AudioIO0, 0)!=0 {
            AudioIO0.Type = 0
            return false
        }

        ; Clone the other 3 IOAudio instances via CopyMem
        ; This preserves AllocKey and channel binding from OpenDevice
        AudioIO1^^ = AudioIO0^^
        AudioIO2^^ = AudioIO0^^
        AudioIO3^^ = AudioIO0^^
        AudioIO0.Unit = 1  ; channel 1
        AudioIO1.Unit = 2  ; channel 2
        AudioIO2.Unit = 4  ; channel 3
        AudioIO3.Unit = 8  ; channel 4

        ; Allocate separate MsgPorts for each cloned IOAudio
        AudioIO0.ReplyPort = init_msgport(msgport0)
        AudioIO1.ReplyPort = init_msgport(msgport1)
        AudioIO2.ReplyPort = init_msgport(msgport2)
        AudioIO3.ReplyPort = init_msgport(msgport3)

        return true

        private sub init_msgport(^^exec.MsgPort port) -> ^^exec.MsgPort {
            port.Type = exec.NT_MSGPORT
            port.Flags = exec.PA_SIGNAL
            port.SigBit = exec.AllocSignal(-1) as ubyte
            port.SigTask = exec.FindTask(0)
            exec.NewList(&port.Head)
            return port
        }
    }

    sub closedevice() {
        if AudioIO0.Type == 0
            return

        exec.CloseDevice(AudioIO0)
        ^^exec.MsgPort port = AudioIO0.ReplyPort
        exec.FreeSignal(port.SigBit as byte)
        port = AudioIO1.ReplyPort
        exec.FreeSignal(port.SigBit as byte)
        port = AudioIO2.ReplyPort
        exec.FreeSignal(port.SigBit as byte)
        port = AudioIO3.ReplyPort
        exec.FreeSignal(port.SigBit as byte)
        AudioIO0.Type = 0
        AudioIO1.Type = 0
        AudioIO2.Type = 0
        AudioIO3.Type = 0
    }

    sub get_io(ubyte channel) -> ^^IOAudio {
        when channel {
            0 -> return AudioIO0
            1 -> return AudioIO1
            2 -> return AudioIO2
            else -> return AudioIO3
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

    private asmsub BeginIO(^^IOAudio io @A1) clobbers (D0, D1, A0, A1, A6) {
        ; Calls the device's BeginIO vector directly, instead of exec DoIO/SendIO,
        ; because those clear io_Flags which would wipe ADIOF_PERVOL and friends.
        %asm {{
            move.l  20(a1), a6          ; a6 = io_Device (offset 20 in IORequest)
            jsr     -30(a6)             ; device BeginIO vector
            rts
        }}
    }
}
