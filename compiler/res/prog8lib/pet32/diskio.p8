%import strings
%import syslib

diskio {
    %option no_symbol_prefixing, ignore_unused

    const ubyte READ_IO_CHANNEL=12
    const ubyte WRITE_IO_CHANNEL=13
    const ubyte STATUS_EOF=$40

    ubyte @shared drivenumber = 8           ; user programs can set this to the drive number they want to load/save to!

    str list_filename = "?" * 50

    sub reset_read_channel() {
        void cbm.CHKIN(READ_IO_CHANNEL)
    }

    sub reset_write_channel() {
        cbm.CHKOUT(WRITE_IO_CHANNEL)
    }

    sub status() -> str {
        ; -- retrieve the disk drive's current status message

        ^^ubyte messageptr = &list_filename
        cbm.SETNAM(0, list_filename)
        cbm.SETLFS(15, drivenumber, 15)
        void cbm.OPEN()          ; open 15,8,15
        void cbm.CHKIN(15)        ; use #15 as input channel

        while cbm.READST()==0 {
            cx16.r5L = cbm.CHRIN()
            if cx16.r5L=='\r' or cx16.r5L=='\n'
                break
            @(messageptr) = cx16.r5L
            messageptr++
        }
        @(messageptr) = 0

done:
        cbm.CLOSE(15)
        cbm.CLRCHN()        ; restore default i/o devices
        return list_filename
    }

    ; similar to above, but instead of fetching the entire string, it only fetches the status code and returns it as ubyte
    ; in case of IO error, returns 255 (CMDR-DOS itself is physically unable to return such a value)
    sub status_code() -> ubyte {
        if cbm.READST()==128 {
            return 255
        }

        cbm.SETNAM(0, list_filename)
        cbm.SETLFS(15, drivenumber, 15)
        void cbm.OPEN()          ; open 15,8,15
        if_cs
            goto io_error
        void cbm.CHKIN(15)        ; use #15 as input channel

        list_filename[0] = cbm.CHRIN()
        list_filename[1] = cbm.CHRIN()
        list_filename[2] = 0

        while cbm.READST()==0 {
            void cbm.CHRIN()
        }

        cbm.CLRCHN()        ; restore default i/o devices
        cbm.CLOSE(15)
        return conv.str2ubyte(list_filename)

io_error:
        cbm.CLRCHN()
        cbm.CLOSE(15)
        return 255
    }
}
