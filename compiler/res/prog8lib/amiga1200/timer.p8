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
        ^^exec.MsgPort timerPort = exec.CreateMsgPort()
        if timerPort == 0 return false
        TimerIO = exec.CreateIORequest(timerPort, sizeof(timer.TimeRequest))
        if TimerIO == 0 {
            exec.DeleteMsgPort(timerPort)
            return false
        }

        if exec.OpenDevice("timer.device", timer.UNIT::MICROHZ, TimerIO, 0)==0 {
            sys.TimerBase = TimerIO.Device
            return true
        }
        exec.DeleteIORequest(TimerIO)
        exec.DeleteMsgPort(timerPort)
        TimerIO = 0
        return false
    }

    sub closedevice() {
        if TimerIO != 0 {
            ^^exec.MsgPort timerPort = TimerIO.ReplyPort
            exec.CloseDevice(TimerIO)
            exec.DeleteIORequest(TimerIO)
            exec.DeleteMsgPort(timerPort)
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
    extsub @bank 18  -60 = ReadEClock( ^^EClockVal dest @A0 ) -> long @D0   ; returns tickrate value
    extsub @bank 18  -66 = GetSysTime( ^^TimeVal dest @A0 )

}
