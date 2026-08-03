%import textio
%import exec

main {
    sub start() {
        ^^exec.MsgPort timerPort = []
        ^^timer.TimeRequest timerIO = []

        timerPort.Type = exec.NT_MSGPORT
        timerPort.Flags = exec.PA_SIGNAL
        timerPort.SigBit = exec.AllocSignal(-1) as ubyte
        timerPort.SigTask = exec.FindTask(0)
        exec.NewList(&&timerPort.Head)
        timerIO.ReplyPort = timerPort

        if exec.OpenDevice("timer.device", timer.UNIT::MICROHZ, timerIO, 0)==0 {
            txt.print("timer.device open\n")

            sys.TimerBase = timerIO.Device
            ^^timer.EClockVal eclock = []
            ^^timer.TimeVal systime = []

             timerIO.Command = timer.TR_GETSYSTIME
             void exec.DoIO(timerIO)
             txt.print_l(timerIO.secs)
             txt.spc()
             txt.print_l(timerIO.micro)
             txt.nl()

;            timer.GetSysTime(systime)
;            txt.print_l(systime.secs)
;            txt.spc()
;            txt.print_l(systime.micro)
;            txt.nl()
;
;            long tickrate = timer.ReadEClock(eclock)
;            txt.print_l(tickrate)
;            txt.nl()
;            txt.print_ulhex(eclock.hi,true)
;            txt.spc()
;            txt.print_ulhex(eclock.lo,false)
;            txt.nl()

            exec.CloseDevice(timerIO)
        }

        exec.FreeSignal(timerPort.SigBit as byte)
    }
}

sys {
    %option merge

    pointer @shared TimerBase
}

timer {

    enum UNIT {
        MICROHZ = 0,
        VBLANK = 1,
        ECLOCK = 2,
        WAITUNTIL  = 3,
        WAITECLOCK = 4
    }

    const uword TR_ADDREQUEST = exec.CMD_NONSTD
    const uword TR_GETSYSTIME = exec.CMD_NONSTD+1
    const uword TR_SETSYSTIME = exec.CMD_NONSTD+2


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


    extsub @bank 18  -42 = AddTime( ^^TimeVal dest @A0, ^^TimeVal src @A1 )
    extsub @bank 18  -48 = SubTime( ^^TimeVal dest @A0, ^^TimeVal src @A1 )
    extsub @bank 18  -54 = CmpTime( ^^TimeVal dest @A0, ^^TimeVal src @A1 ) -> long @D0
    extsub @bank 18  -60 = ReadEClock( ^^EClockVal dest @A0 ) -> long @D0   ; kickstart 2.0+, returns tickrate value
    extsub @bank 18  -66 = GetSysTime( ^^TimeVal dest @A0 )     ; kickstart 2.0+

}
