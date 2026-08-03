
timer {
    %option no_symbol_prefixing, ignore_unused

    enum UNIT {
        MICROHZ = 0,
        VBLANK = 1,
        ECLOCK = 2,
        WAITUNTIL  = 3,
        WAITECLOCK = 4
    }

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

    sub getsystime() -> long, long {
        sys.TimerIO.Command = timer.TR_GETSYSTIME
        void exec.DoIO(sys.TimerIO)
        return sys.TimerIO.secs, sys.TimerIO.micro
    }

    sub setsystime(long secs, long micro) {
        sys.TimerIO.Command = timer.TR_SETSYSTIME
        sys.TimerIO.secs = secs
        sys.TimerIO.micro = micro
        void exec.DoIO(sys.TimerIO)
    }

    extsub @bank 18  -42 = AddTime( ^^TimeVal dest @A0, ^^TimeVal src @A1 )
    extsub @bank 18  -48 = SubTime( ^^TimeVal dest @A0, ^^TimeVal src @A1 )
    extsub @bank 18  -54 = CmpTime( ^^TimeVal dest @A0, ^^TimeVal src @A1 ) -> long @D0
    extsub @bank 18  -60 = ReadEClock( ^^EClockVal dest @A0 ) -> long @D0   ; kickstart 2.0+, returns tickrate value
    extsub @bank 18  -66 = GetSysTime( ^^TimeVal dest @A0 )     ; kickstart 2.0+

}
