%import exec

trackdisk {
    %option merge, no_symbol_prefixing, ignore_unused

    ^^exec.IOStdReq @shared TrackIO

    ; trackdisk.device driver commands (from <devices/trackdisk.h>):
    const uword TD_MOTOR = 9
    const uword TD_SEEK = 9+1
    const uword TD_FORMAT = 9+2
    const uword TD_REMOVE = 9+3
    const uword TD_CHANGENUM = 9+4
    const uword TD_CHANGESTATE = 9+5
    const uword TD_PROTSTATUS = 9+6
    const uword TD_RAWREAD = 9+7
    const uword TD_RAWWRITE = 9+8
    const uword TD_GETDRIVETYPE = 9+9
    const uword TD_GETNUMTRACKS = 9+10
    const uword TD_ADDCHANGEINT = 9+11
    const uword TD_REMCHANGEINT = 9+12
    const uword TD_GETGEOMETRY = 9+13
    const uword TD_EJECT = 9+14

    ; physical drive constants:
    const ubyte NUMSECS = 11
    const ubyte NUMUNITS = 4
    const uword TD_SECTOR = 512
    const ubyte TD_SECSHIFT = 9
    const long TRACK_BYTES = 5632        ; NUMSECS*TD_SECTOR, one full track
    const ubyte TD_LABELSIZE = 16

    ; drive types returned by getdrivetype():
    const ubyte DRIVE3_5 = 1
    const ubyte DRIVE5_25 = 2
    const ubyte DRIVE3_5_150RPM = 3

    ; trackdisk.device error codes (from <devices/trackdisk.h>):
    const ubyte TDERR_NotSpecified = 20
    const ubyte TDERR_NoSecHdr = 21
    const ubyte TDERR_BadSecPreamble = 22
    const ubyte TDERR_BadSecID = 23
    const ubyte TDERR_BadHdrSum = 24
    const ubyte TDERR_BadSecSum = 25
    const ubyte TDERR_TooFewSecs = 26
    const ubyte TDERR_BadSecHdr = 27
    const ubyte TDERR_WriteProt = 28
    const ubyte TDERR_DiskChanged = 29
    const ubyte TDERR_SeekError = 30
    const ubyte TDERR_NoMem = 31
    const ubyte TDERR_BadUnitNum = 32
    const ubyte TDERR_BadDriveType = 33
    const ubyte TDERR_DriveInUse = 34
    const ubyte TDERR_PostReset = 35


    struct DriveGeometry {
        ; layout returned by TD_GETGEOMETRY (see <devices/trackdisk.h>)
        long SectorSize      ; 0, in bytes
        long TotalSectors    ; 4, total # of sectors on drive
        long Cylinders       ; 8, number of cylinders
        long CylSectors      ; 12, number of sectors/cylinder
        long Heads           ; 16, number of surfaces
        long TrackSectors    ; 20, number of sectors/track
        long BufMemType      ; 24, preferred buffer memory type
        ubyte DeviceType     ; 28
        ubyte Flags          ; 29
        uword Reserved       ; 30
    }

    sub read(pointer buffer, long length, long offset) -> byte {
        ; -- Read sectors from the disk (CMD_READ).
        ; offset is the byte offset from the start of the disk, length the number
        ; of bytes to read; both must be multiples of TD_SECTOR (512).
        ; On Kickstart < V36 the buffer must be in chip RAM.
        ; Returns the I/O error code (0 = success).
        TrackIO.Command = exec.CMD_READ
        TrackIO.Flags = 0
        TrackIO.Data = buffer
        TrackIO.IOStdReq_Length = length
        TrackIO.Offset = offset
        void exec.DoIO(TrackIO)
        return TrackIO.Error
    }

    sub write(pointer buffer, long length, long offset) -> byte {
        ; -- Write sectors to the disk (CMD_WRITE). Same alignment rules as read().
        ; Returns the I/O error code (0 = success).
        TrackIO.Command = exec.CMD_WRITE
        TrackIO.Flags = 0
        TrackIO.Data = buffer
        TrackIO.IOStdReq_Length = length
        TrackIO.Offset = offset
        void exec.DoIO(TrackIO)
        return TrackIO.Error
    }

    sub update() -> byte {
        ; -- Flush the track buffer to disk if dirty (CMD_UPDATE).
        ; Returns the I/O error code (0 = success).
        TrackIO.Command = exec.CMD_UPDATE
        TrackIO.Flags = 0
        void exec.DoIO(TrackIO)
        return TrackIO.Error
    }

    sub motor(bool on) -> byte {
        ; -- Turn the drive motor on or off (TD_MOTOR). The device turns the
        ; motor on automatically when needed; turning it off is your job.
        ; Returns the I/O error code (0 = success); TrackIO.Actual then holds
        ; the previous motor state.
        TrackIO.Command = trackdisk.TD_MOTOR
        TrackIO.Flags = 0
        if on
            TrackIO.IOStdReq_Length = 1
        else
            TrackIO.IOStdReq_Length = 0
        void exec.DoIO(TrackIO)
        return TrackIO.Error
    }

    sub seek(long offset) -> byte {
        ; -- Move the drive heads to the track holding the given byte offset
        ; (TD_SEEK). Diagnostics use only; position is not verified until the
        ; next read. Returns the I/O error code (0 = success).
        TrackIO.Command = trackdisk.TD_SEEK
        TrackIO.Flags = 0
        TrackIO.Offset = offset
        void exec.DoIO(TrackIO)
        return TrackIO.Error
    }

    sub changestate() -> bool {
        ; -- Check whether a disk is in the drive (TD_CHANGESTATE).
        ; Returns true when a disk is present (TrackIO.Error holds the
        ; I/O error code, 0 = success).
        TrackIO.Command = trackdisk.TD_CHANGESTATE
        TrackIO.Flags = 0
        void exec.DoIO(TrackIO)
        return TrackIO.Actual==0
    }

    sub protstatus() -> bool {
        ; -- Check whether the disk is write-protected (TD_PROTSTATUS).
        ; Returns true when the disk IS write-protected (TrackIO.Error holds
        ; the I/O error code, 0 = success).
        TrackIO.Command = trackdisk.TD_PROTSTATUS
        TrackIO.Flags = 0
        void exec.DoIO(TrackIO)
        return TrackIO.Actual!=0
    }

    sub getnumtracks() -> ubyte {
        ; -- Return the number of tracks on this unit (TD_GETNUMTRACKS).
        ; No drive reports more than 255 tracks (a DD/HD floppy has 160),
        ; so this fits in a byte. Check TrackIO.Error first.
        TrackIO.Command = trackdisk.TD_GETNUMTRACKS
        TrackIO.Flags = 0
        void exec.DoIO(TrackIO)
        return TrackIO.Actual as ubyte
    }

    sub getdrivetype() -> long {
        ; -- Return the drive type (TD_GETDRIVETYPE): one of DRIVE3_5,
        ; DRIVE5_25, DRIVE3_5_150RPM. Check TrackIO.Error first.
        TrackIO.Command = trackdisk.TD_GETDRIVETYPE
        TrackIO.Flags = 0
        void exec.DoIO(TrackIO)
        return TrackIO.Actual
    }

    sub getgeometry(^^DriveGeometry geom) -> byte {
        ; -- Return the drive geometry in the given struct (TD_GETGEOMETRY).
        ; Returns the I/O error code (0 = success).
        TrackIO.Command = trackdisk.TD_GETGEOMETRY
        TrackIO.Flags = 0
        TrackIO.Data = geom
        TrackIO.IOStdReq_Length = sizeof(trackdisk.DriveGeometry)
        void exec.DoIO(TrackIO)
        return TrackIO.Error
    }

}
