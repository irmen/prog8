%import exec
%import shared_amiga_trackdisk

trackdisk {
    %option no_symbol_prefixing, ignore_unused

    sub opendevice(ubyte unit) -> bool {
        ; open a trackdisk unit (0-3 = DF0:-DF3:)
        ^^exec.MsgPort tdPort = exec.CreateMsgPort()
        if tdPort == 0 return false
        TrackIO = exec.CreateIORequest(tdPort, sizeof(exec.IOStdReq))
        if TrackIO == 0 {
            exec.DeleteMsgPort(tdPort)
            return false
        }

        if exec.OpenDevice("trackdisk.device", unit, TrackIO, 0)==0 {
            return true
        }
        exec.DeleteIORequest(TrackIO)
        exec.DeleteMsgPort(tdPort)
        TrackIO = 0
        return false
    }

    sub closedevice() {
        if TrackIO != 0 {
            ^^exec.MsgPort tdPort = TrackIO.ReplyPort
            exec.CloseDevice(TrackIO)
            exec.DeleteIORequest(TrackIO)
            exec.DeleteMsgPort(tdPort)
            TrackIO = 0
        }
    }

}
