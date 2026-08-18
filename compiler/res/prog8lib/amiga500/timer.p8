%import exec

timer {
    %option no_symbol_prefixing, ignore_unused

    ^^TimeRequest @shared TimerIO

    enum UNIT {
        MICROHZ = 0,
        VBLANK = 1,
        ECLOCK = 2,
        WAITUNTIL  = 3,
        WAITECLOCK = 4
    }

    ; timer.device IO commands:
    const uword TR_ADDREQUEST = 9
    const uword TR_GETSYSTIME = 9+1
    const uword TR_SETSYSTIME = 9+2


    struct TimeVal {
        long secs
        long micro
    }

    struct TimeRequest {
        ; struct IORequest
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
        ; struct TimeVal
        long secs  ; 32
        long micro ; 36
    }

    struct EClockVal {
        ; This is really a 64 bit integer value split into two 32 bit integers
        long hi
        long lo
    }

    sub opendevice() -> bool {
        ; open timer.device in a kickstart 1.3 compatible fashion
        ^^exec.MsgPort timerPort = exec.AllocMem(sizeof(exec.MsgPort), exec.MEMF_PUBLIC | exec.MEMF_CLEAR)
        if timerPort == 0 return false
        TimerIO = exec.AllocMem(sizeof(timer.TimeRequest), exec.MEMF_PUBLIC | exec.MEMF_CLEAR)
        if TimerIO == 0 {
            exec.FreeMem(timerPort, sizeof(exec.MsgPort))
            return false
        }
        timerPort.Type = exec.NT_MSGPORT
        timerPort.Flags = exec.PA_SIGNAL
        timerPort.SigBit = exec.AllocSignal(-1) as ubyte
        timerPort.SigTask = exec.FindTask(0)
        ^^exec.List listPtr = &&timerPort.Head as ^^exec.List
        exec.NewList(listPtr)
        TimerIO.ReplyPort = timerPort

        if exec.OpenDevice("timer.device", timer.UNIT::MICROHZ, TimerIO, 0)==0 {
            sys.TimerBase = TimerIO.Device
            return true
        }
        exec.FreeSignal(timerPort.SigBit as byte)
        exec.FreeMem(TimerIO, sizeof(timer.TimeRequest))
        exec.FreeMem(timerPort, sizeof(exec.MsgPort))
        TimerIO = 0
        return false
    }

    sub closedevice() {
        if TimerIO != 0 {
            ^^exec.MsgPort timerPort = TimerIO.ReplyPort
            byte sigbit = timerPort.SigBit as byte
            exec.CloseDevice(TimerIO)
            exec.FreeSignal(sigbit)
            exec.FreeMem(TimerIO, sizeof(timer.TimeRequest))
            exec.FreeMem(timerPort, sizeof(exec.MsgPort))
            TimerIO = 0
        }
    }

    sub getsystime() -> long, long {
        TimerIO.Command = timer.TR_GETSYSTIME
        void exec.DoIO(TimerIO)
        return TimerIO.secs, TimerIO.micro
    }

    sub setsystime(long secs, long micro) {
        TimerIO.Command = timer.TR_SETSYSTIME
        TimerIO.secs = secs
        TimerIO.micro = micro
        void exec.DoIO(TimerIO)
    }

    extsub @bank 18  -42 = AddTime( ^^TimeVal dest @A0, ^^TimeVal src @A1 )
    extsub @bank 18  -48 = SubTime( ^^TimeVal dest @A0, ^^TimeVal src @A1 )
    extsub @bank 18  -54 = CmpTime( ^^TimeVal dest @A0, ^^TimeVal src @A1 ) -> long @D0
    extsub @bank 18  -60 = ReadEClock( ^^EClockVal dest @A0 ) -> long @D0   ; kickstart 2.0+, returns tickrate value
    extsub @bank 18  -66 = GetSysTime( ^^TimeVal dest @A0 )     ; kickstart 2.0+

}
