%import textio
%import syslib

; Note: this code is compatible with C64 and CX16.

diskio {


    sub directory(ubyte drivenumber) -> byte {
        ; -- Shows the directory contents of disk drive 8-11 (provide as argument).

        c64.SETNAM(1, "$")
        c64.SETLFS(1, drivenumber, 0)
        void c64.OPEN()          ; open 1,8,0,"$"
        if_cs
            goto io_error
        void c64.CHKIN(1)        ; use #1 as input channel
        if_cs
            goto io_error

        repeat 4 {
            void c64.CHRIN()     ; skip the 4 prologue bytes
        }

        ; while not key pressed / EOF encountered, read data.
        ubyte status = c64.READST()
        while not status {
            txt.print_uw(mkword(c64.CHRIN(), c64.CHRIN()))
            txt.chrout(' ')
            ubyte @zp char
            do {
                char = c64.CHRIN()
                txt.chrout(char)
            } until char==0
            txt.chrout('\n')
            void c64.CHRIN()     ; skip 2 bytes
            void c64.CHRIN()
            status = c64.READST()
            void c64.STOP()
            if_nz
                break
        }

io_error:
        status = c64.READST()
        c64.CLRCHN()        ; restore default i/o devices
        c64.CLOSE(1)

        if status and status != 64 {            ; 64=end of file
            txt.print("\ni/o error, status: ")
            txt.print_ub(status)
            txt.chrout('\n')
            return false
        }

        return true
    }


    sub status(ubyte drivenumber) {
        ; -- display the disk drive's current status message
        c64.SETNAM(0, $0000)
        c64.SETLFS(15, drivenumber, 15)
        void c64.OPEN()          ; open 15,8,15
        if_cs
            goto io_error
        void c64.CHKIN(15)        ; use #15 as input channel
        if_cs
            goto io_error

        while not c64.READST()
            txt.chrout(c64.CHRIN())

io_error:
        c64.CLRCHN()        ; restore default i/o devices
        c64.CLOSE(15)
    }


    str filename = "0:??????????????????????????????????????"

    sub save(ubyte drivenumber, uword filenameptr, uword address, uword size) -> byte {
        ubyte flen = strlen(filenameptr)
        filename[0] = '0'
        filename[1] = ':'
        memcopy(filenameptr, &filename+2, flen)
        memcopy(",s,w", &filename+2+flen, 5)
        c64.SETNAM(flen+6, filename)
        c64.SETLFS(1, drivenumber, 1)
        void c64.OPEN()
        if_cs
            goto io_error

        c64.CHKOUT(1)
        if_cs
            goto io_error

        repeat size {
            c64.CHROUT(@(address))
            address++
        }

io_error:
        c64.CLRCHN()
        c64.CLOSE(1)
        return c64.READST()==0
    }

    sub load(ubyte drivenumber, uword filenameptr, uword address) -> byte {
        ubyte flen = strlen(filenameptr)
        filename[0] = '0'
        filename[1] = ':'
        memcopy(filenameptr, &filename+2, flen)
        memcopy(",s,r", &filename+2+flen, 5)
        c64.SETNAM(flen+6, filename)
        c64.SETLFS(1, drivenumber, 2)
        void c64.OPEN()
        if_cs
            goto io_error
        void c64.CHKIN(1)
        if_cs
            goto io_error

        do {
            @(address) = c64.CHRIN()
            address++
        } until c64.READST()

io_error:
        c64.CLRCHN()
        c64.CLOSE(1)

        ubyte status = c64.READST()
        return status==0 or status==64
    }

    sub delete(ubyte drivenumber, uword filenameptr) {
        ; -- delete a file on the drive
        ubyte flen = strlen(filenameptr)
        filename[0] = 's'
        filename[1] = ':'
        memcopy(filenameptr, &filename+2, flen+1)
        c64.SETNAM(flen+2, filename)
        c64.SETLFS(1, drivenumber, 15)
        void c64.OPEN()
        c64.CLRCHN()
        c64.CLOSE(1)
    }

    sub rename(ubyte drivenumber, uword oldfileptr, uword newfileptr) {
        ; -- rename a file on the drive
        ubyte flen_old = strlen(oldfileptr)
        ubyte flen_new = strlen(newfileptr)
        filename[0] = 'r'
        filename[1] = ':'
        memcopy(newfileptr, &filename+2, flen_new)
        filename[flen_new+2] = '='
        memcopy(oldfileptr, &filename+3+flen_new, flen_old+1)
        c64.SETNAM(3+flen_new+flen_old, filename)
        c64.SETLFS(1, drivenumber, 15)
        void c64.OPEN()
        c64.CLRCHN()
        c64.CLOSE(1)
    }
}
