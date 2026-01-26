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


    ; Use kernal LOAD routine to load the given program file in memory.
    ; This is similar to Basic's  LOAD "filename",drive  /  LOAD "filename",drive,1
    ; If you don't give an address_override, the location in memory is taken from the 2-byte file header.
    ; If you specify a custom address_override, the first 2 bytes in the file are ignored
    ; and the rest is loaded at the given location in memory.
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    sub load(str filenameptr, uword address_override) -> uword  {
        cbm.SETNAM(strings.length(filenameptr), filenameptr)
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        void cbm.OPEN()
        void cbm.CHKIN(READ_IO_CHANNEL)
        if address_override==0 {
            ; fetch load address from prg header first 2 bytes
            %asm {{
                jsr  cbm.CHRIN
                sta  P8ZP_SCRATCH_PTR
                jsr  cbm.CHRIN
                sta  P8ZP_SCRATCH_PTR+1
            }}
        } else {
            ; skip the load address from the prg header
            %asm {{
                jsr  cbm.CHRIN
                jsr  cbm.CHRIN
                lda  address_override
                ldy  address_override+1
                sta  P8ZP_SCRATCH_PTR
                sty  P8ZP_SCRATCH_PTR+1
            }}
        }

        if cbm.READST()!=0 {
            cx16.r0 = 0
            goto end
        }

readloop:
        %asm {{
            jsr  cbm.READST
            cmp  #STATUS_EOF
            beq  _done
            cmp  #0
            bne  _error
            jsr  cbm.CHRIN
            ldy  #0
            sta  (P8ZP_SCRATCH_PTR),y
            inc  P8ZP_SCRATCH_PTR
            bne  readloop
            inc  P8ZP_SCRATCH_PTR+1
            bne  readloop

_error:
            lda  #0
            beq  _done2
_done:
            lda  P8ZP_SCRATCH_PTR
            ldy  P8ZP_SCRATCH_PTR+1
_done2:
            sta  cx16.r0L
            sty  cx16.r0H
        }}

end:
        cbm.CLOSE(READ_IO_CHANNEL)
        cbm.CLRCHN()        ; restore default i/o devices
        return cx16.r0
    }

    ; Identical to load(), but DOES INCLUDE the first 2 bytes in the file.
    ; No program header is assumed in the file. Everything is loaded.
    ; See comments on load() for more details.
    sub load_raw(str filenameptr, uword start_address) -> uword {
        cbm.SETNAM(strings.length(filenameptr), filenameptr)
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        void cbm.OPEN()
        void cbm.CHKIN(READ_IO_CHANNEL)
        %asm {{
            lda  start_address
            ldy  start_address+1
            sta  P8ZP_SCRATCH_PTR
            sty  P8ZP_SCRATCH_PTR+1
        }}
        goto diskio.load.readloop
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
