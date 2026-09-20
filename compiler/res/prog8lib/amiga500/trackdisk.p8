%import exec
%import shared_amiga_trackdisk

trackdisk {
    %option no_symbol_prefixing, ignore_unused

    sub opendevice(long unit) -> bool {
        ; open a trackdisk unit (0-3 = DF0:-DF3:) in a kickstart 1.3 compatible fashion
        ^^exec.MsgPort tdPort = exec.AllocMem(sizeof(exec.MsgPort), exec.MEMF_PUBLIC | exec.MEMF_CLEAR)
        if tdPort == 0 return false
        TrackIO = exec.AllocMem(sizeof(exec.IOStdReq), exec.MEMF_PUBLIC | exec.MEMF_CLEAR)
        if TrackIO == 0 {
            exec.FreeMem(tdPort, sizeof(exec.MsgPort))
            return false
        }
        tdPort.Type = exec.NT_MSGPORT
        tdPort.Flags = exec.PA_SIGNAL
        tdPort.SigBit = exec.AllocSignal(-1) as ubyte
        tdPort.SigTask = exec.FindTask(0)
        ^^exec.List listPtr = &&tdPort.Head as ^^exec.List
        exec.NewList(listPtr)
        TrackIO.ReplyPort = tdPort

        if exec.OpenDevice("trackdisk.device", unit, TrackIO, 0)==0 {
            return true
        }
        exec.FreeSignal(tdPort.SigBit as byte)
        exec.FreeMem(TrackIO, sizeof(exec.IOStdReq))
        exec.FreeMem(tdPort, sizeof(exec.MsgPort))
        TrackIO = 0
        return false
    }

    sub closedevice() {
        if TrackIO != 0 {
            ^^exec.MsgPort tdPort = TrackIO.ReplyPort
            byte sigbit = tdPort.SigBit as byte
            exec.CloseDevice(TrackIO)
            exec.FreeSignal(sigbit)
            exec.FreeMem(TrackIO, sizeof(exec.IOStdReq))
            exec.FreeMem(tdPort, sizeof(exec.MsgPort))
            TrackIO = 0
        }
    }

}
