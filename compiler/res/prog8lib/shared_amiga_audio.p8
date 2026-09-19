%import exec
%import syslib

audio {
    %option merge, no_symbol_prefixing, ignore_unused

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
        ^^exec.Node Succ  ; 0
        ^^exec.Node Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        ^^exec.MsgPort ReplyPort  ; 14
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
        ^^exec.Node Msg_Succ  ; 48
        ^^exec.Node Msg_Pred  ; 52
        ubyte Msg_Type  ; 56
        byte Msg_Pri  ; 57
        str Msg_Name  ; 58
        ^^exec.MsgPort Msg_ReplyPort  ; 62
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

    ; Separate control-request structures for the ADCMD_* control commands.
    ; An IORequest carrying an in-flight CMD_WRITE is queued inside audio.device
    ; and must not be reused for another command, so the controls get their own.
    private ^^IOAudio CtrlIO0 = []
    private ^^IOAudio CtrlIO1 = []
    private ^^IOAudio CtrlIO2 = []
    private ^^IOAudio CtrlIO3 = []

    ; Second write-request per channel for gapless streaming (see play_queued).
    ; One IOAudio block cannot hold two outstanding CMD_WRITEs, so each channel
    ; gets an alternate request block with its own reply port; the two are used
    ; ping-pong fashion and audio.device queues them back-to-back.
    ^^IOAudio StreamIO0 = []
    ^^IOAudio StreamIO1 = []
    ^^IOAudio StreamIO2 = []
    ^^IOAudio StreamIO3 = []

    private ^^exec.MsgPort sport0 = []
    private ^^exec.MsgPort sport1 = []
    private ^^exec.MsgPort sport2 = []
    private ^^exec.MsgPort sport3 = []

    ; per-channel streaming bookkeeping: number of outstanding writes (0..2),
    ; and which request block takes the next play / the next wait (oldest first).
    private ubyte[4] queued
    private bool[4] play_alt
    private bool[4] wait_alt

    ; ---- high level audio interface ----

    sub init() -> bool {
        ; -- Initialize the audio device on all 4 channels.
        ubyte[1] channel_matrix = [15]      ; allocate all 4 channels at once
        for ubyte channel in 0 to 3 {
            active_channels[channel] = false
            queued[channel] = 0
            play_alt[channel] = false
            wait_alt[channel] = false
        }
        return opendevice(channel_matrix, 1, 0)
    }

    sub closedown() {
        ; -- Close down the audio device on all 4 channels. Does not wait for sounds to finish playing.
        stop_all()
        closedevice()
        for ubyte channel in 0 to 3
            active_channels[channel] = false
    }

    sub play(ubyte channel, ^^byte samples, long num_samples, uword sample_rate, ubyte volume, uword cycles) {
        ; -- Play a sample asynchronously on the given channel (0-3), with the given parameters.
        ; If chunks were queued earlier with play_queued, those are waited for first,
        ; so this never reuses an IOAudio block that still has a write in flight.
        while queued[channel]>0
            wait_queued(channel)
        ^^IOAudio io = get_io(channel)
        io.Command = exec.CMD_WRITE
        io.Flags = ADIOF_PERVOL
        io.Data = samples
        io.IOAudio_Length = num_samples
        io.Period = period(sample_rate)
        io.Volume = volume
        io.Cycles = cycles
        BeginIO(io)  ; not exec.DoIO/SendIO: those clear io_Flags, wiping ADIOF_PERVOL!
        queued[channel] = 1
        play_alt[channel] = true   ; primary block is now in flight, next play uses the alternate
        active_channels[channel] = true
        ; sound now plays asynchronously.
    }

    sub play_queued(ubyte channel, ^^byte samples, long num_samples, uword sample_rate, ubyte volume, uword cycles) {
        ; -- Queue a sample chunk for gapless streaming on the given channel (0-3).
        ; Unlike play(), this may be called while the previous chunk is still playing:
        ; audio.device queues the CMD_WRITE and starts it the moment the previous
        ; chunk ends, so back-to-back calls produce continuous audio with no gaps.
        ; Each channel has two request slots; if both are full this waits for the
        ; oldest chunk to finish first (which returns instantly in a healthy stream).
        ; NOTE: the NDK requires ioa_Length to be even (2..131072), so an odd
        ; length is rounded down by one sample and lengths below 2 are ignored.
        if (num_samples & 1) != 0
            num_samples = num_samples-1
        if num_samples<2
            return
        if queued[channel]==2
            wait_queued(channel)
        ^^IOAudio io = get_queued_io(channel, play_alt[channel])
        play_alt[channel] = not play_alt[channel]
        io.Command = exec.CMD_WRITE
        io.Flags = ADIOF_PERVOL
        io.Data = samples
        io.IOAudio_Length = num_samples
        io.Period = period(sample_rate)
        io.Volume = volume
        io.Cycles = cycles
        BeginIO(io)  ; not exec.DoIO/SendIO: those clear io_Flags, wiping ADIOF_PERVOL!
        queued[channel] = queued[channel]+1
        active_channels[channel] = true
        ; chunk plays (or stays queued) asynchronously.
    }

    sub wait_channel(ubyte channel) {
        ; wait for the current sound(s) on this channel to finish playing.
        while queued[channel]>0
            wait_queued(channel)
    }

    sub wait_queued(ubyte channel) {
        ; -- Wait for the oldest outstanding queued chunk on this channel.
        if queued[channel]==0
            return
        reap_oldest(channel, false)
    }

    private sub get_queued_io(ubyte channel, bool alt) -> ^^IOAudio {
        ; request block taking the next play/wait on this channel (primary or alternate).
        if not alt
            return get_io(channel)
        when channel {
            0 -> return StreamIO0
            1 -> return StreamIO1
            2 -> return StreamIO2
            else -> return StreamIO3
        }
    }

    private sub reap_oldest(ubyte channel, bool abort) {
        ; reap the oldest outstanding write on this channel (optionally abort it first).
        ^^IOAudio io = get_queued_io(channel, wait_alt[channel])
        if abort
            exec.AbortIO(io)
        void exec.WaitIO(io)
        wait_alt[channel] = not wait_alt[channel]
        queued[channel] = queued[channel]-1
        if queued[channel]==0 {
            active_channels[channel] = false
            play_alt[channel] = false
            wait_alt[channel] = false
        }
    }

    sub wait_all() {
        ; wait for all channels to finish playing their sounds.
        wait_channel(0)
        wait_channel(1)
        wait_channel(2)
        wait_channel(3)
    }

    sub stop(ubyte channel) {
        ; -- Abort the sound currently playing on this channel (ADCMD_FINISH).
        ; Aborts immediately and reaps every outstanding write, oldest first,
        ; so no queued chunk can play out afterwards.
        if not active_channels[channel]
            return
        ^^IOAudio ctrl = get_ctrl(channel)
        ctrl.Command = ADCMD_FINISH
        ctrl.Flags = 0
        BeginIO(ctrl)
        void exec.WaitIO(ctrl)                ; reap the control request
        while queued[channel]>0
            reap_oldest(channel, true)
    }

    sub stop_all() {
        ; -- Abort the sounds currently playing on all channels.
        stop(0)
        stop(1)
        stop(2)
        stop(3)
    }

    sub set_freqvol(ubyte channel, uword samplerate, ubyte volume) {
        ; -- Change the playback rate and volume of the sound currently playing on this channel (ADCMD_PERVOL).
        ; Takes effect immediately. Both are always set together: ADCMD_PERVOL has no way to change just one.
        if not active_channels[channel]
            return
        ^^IOAudio ctrl = get_ctrl(channel)
        ctrl.Command = ADCMD_PERVOL
        ctrl.Flags = 0
        ctrl.Period = period(samplerate)
        ctrl.Volume = volume
        BeginIO(ctrl)
    }

    inline sub is_playing(ubyte channel) -> bool {
        ; -- Returns true while a sound is playing on this channel,
        ; i.e. between play() and wait_channel()/wait_all()/stop().
        return active_channels[channel]
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

        ; Clone the channel bindings into the control structures
        CtrlIO0^^ = AudioIO0^^
        CtrlIO1^^ = AudioIO1^^
        CtrlIO2^^ = AudioIO2^^
        CtrlIO3^^ = AudioIO3^^

        ; Clone the alternate streaming request blocks (with their own reply ports)
        StreamIO0^^ = AudioIO0^^
        StreamIO1^^ = AudioIO1^^
        StreamIO2^^ = AudioIO2^^
        StreamIO3^^ = AudioIO3^^
        StreamIO0.ReplyPort = init_msgport(sport0)
        StreamIO1.ReplyPort = init_msgport(sport1)
        StreamIO2.ReplyPort = init_msgport(sport2)
        StreamIO3.ReplyPort = init_msgport(sport3)

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
        port = StreamIO0.ReplyPort
        exec.FreeSignal(port.SigBit as byte)
        port = StreamIO1.ReplyPort
        exec.FreeSignal(port.SigBit as byte)
        port = StreamIO2.ReplyPort
        exec.FreeSignal(port.SigBit as byte)
        port = StreamIO3.ReplyPort
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

    private sub get_ctrl(ubyte channel) -> ^^IOAudio {
        when channel {
            0 -> return CtrlIO0
            1 -> return CtrlIO1
            2 -> return CtrlIO2
            else -> return CtrlIO3
        }
    }

    sub period(uword samplerate) -> uword {
        ; calculate the Period to use for the desired sample rate
        return clock() / samplerate as uword
    }

    sub sample_rate(uword per) -> uword {
        ; calculate the sample rate belonging to the given Period; the inverse of period()
        return clock() / per as uword
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
