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

    sub directory() -> bool {
        ; -- Prints the directory contents to the screen. Returns success.

        cbm.SETNAM(1, "$")
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        ubyte status = 1
        void cbm.OPEN()          ; open 12,8,0,"$"
        void cbm.CHKIN(READ_IO_CHANNEL)

        repeat 4 {
            void cbm.CHRIN()     ; skip the 4 prologue bytes
        }

        ; while not stop key pressed / EOF encountered, read data.
        status = cbm.READST()
        if status!=0 {
            status = 1
            goto io_error
        }

        while status==0 {
            ubyte low = cbm.CHRIN()
            ubyte high = cbm.CHRIN()
            txt.print_uw(mkword(high, low))
            txt.spc()
            ubyte @zp character
            repeat {
                character = cbm.CHRIN()
                if character==0
                    break
                txt.chrout(character)
            }
            txt.nl()
            void cbm.CHRIN()     ; skip 2 bytes
            void cbm.CHRIN()
            status = cbm.READST()
            void cbm.STOP()
            if_z
                break
        }
        status = cbm.READST()

io_error:
        cbm.CLRCHN()        ; restore default i/o devices
        cbm.CLOSE(READ_IO_CHANNEL)

        if status!=0 and status & STATUS_EOF == 0 {
            txt.print("\ni/o error, status: ")
            txt.print_ub(status)
            txt.nl()
            return false
        }

        return true
    }

    sub list_filenames(str pattern_ptr, uword filenames_buffer, uword filenames_buf_size) -> ubyte {
        ; -- fill the provided buffer with the names of the files on the disk (until buffer is full).
        ;    Files in the buffer are separated by a 0 byte. You can provide an optional pattern to match against.
        ;    After the last filename one additional 0 byte is placed to indicate the end of the list.
        ;    Returns number of files (it skips 'dir' entries i.e. subdirectories).
        ;    Also sets carry on exit: Carry clear = all files returned, Carry set = directory has more files that didn't fit in the buffer.
        ;    Note that no list of pointers of some form is returned, the names are just squashed together.
        ;    If you really need a list of pointers to the names, that is pretty straightforward to construct by iterating over the names
        ;    and registering when the next one starts after the 0-byte separator.

        ubyte files_found = 0
        uword buf_ptr = filenames_buffer
        @(buf_ptr) = 0
        cbm.SETNAM(1, "$")
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        ubyte status = 1
        void cbm.OPEN()          ; open 12,8,0,"$"
        void cbm.CHKIN(READ_IO_CHANNEL)

        repeat 4 {
            void cbm.CHRIN()     ; skip the 4 prologue bytes
        }

        ; while not stop key pressed / EOF encountered, read data.
        status = cbm.READST()
        if status!=0 {
            status = 1
            goto io_error
        }

        repeat 4 void cbm.CHRIN()    ; skip the 4 prologue bytes

        while cbm.CHRIN()!=0 {
            ; skip the disk name
        }

        while status==0 {
            while cbm.CHRIN()!='"' {
                if cbm.READST()!=0
                    goto end_listing
                ; read up to first quote
            }

            ^^ubyte name_ptr = list_filename

            ; read and store file name
            repeat {
                cx16.r0L = cbm.CHRIN()
                if cx16.r0L=='"'
                    break
                if cx16.r0L==0
                    cx16.r0L = '?'
                @(name_ptr) = cx16.r0L
                name_ptr++
            }
            @(name_ptr) = 0

            if pattern_ptr==0 or strings.pattern_match(list_filename, pattern_ptr) {
                buf_ptr += strings.copy(list_filename, buf_ptr) + 1
                files_found++
            }

            while cbm.CHRIN()!=0 {
                ; read up to first quote
            }
            void cbm.CHRIN()
            void cbm.CHRIN()

            status = cbm.READST()
            void cbm.STOP()
            if_z
                break
        }

end_listing:
        @(buf_ptr) = 0
        status = cbm.READST()

io_error:
        cbm.CLRCHN()        ; restore default i/o devices
        cbm.CLOSE(READ_IO_CHANNEL)
        return files_found
    }

    sub diskname() -> str {
        ; -- Returns pointer to disk name string or 0 if failure.

        cbm.SETNAM(1, "$")
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        bool okay = false
        void cbm.OPEN()          ; open 12,8,0,"$"
        void cbm.CHKIN(READ_IO_CHANNEL)
        void cbm.CHRIN()
        if cbm.READST()!=0
            goto io_error

        while cbm.CHRIN()!='"' {
            ; skip up to entry name
        }

        cx16.r0 = &list_filename
        repeat {
            @(cx16.r0) = cbm.CHRIN()
            if @(cx16.r0)=='"' {
                @(cx16.r0) = ' '
                while @(cx16.r0)==' ' and cx16.r0>=&list_filename {
                    @(cx16.r0) = 0
                    cx16.r0--
                }
                break
            }
            cx16.r0++
        }
        okay = true

io_error:
        cbm.CLRCHN()        ; restore default i/o devices
        cbm.CLOSE(READ_IO_CHANNEL)
        if okay
            return &list_filename
        return 0
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
            address_override = 0
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
            sta  address_override
            sty  address_override+1
        }}

end:
        cbm.CLOSE(READ_IO_CHANNEL)
        cbm.CLRCHN()        ; restore default i/o devices
        return address_override
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


    ; saves a block of memory to disk, including the default 2 byte prg header.
    sub save(str filenameptr, uword startaddress, uword savesize) -> bool {
        bool skip_header = false
internal_save:
        cbm.SETNAM(strings.length(filenameptr), filenameptr)
        cbm.SETLFS(WRITE_IO_CHANNEL, drivenumber, 1)
        void cbm.OPEN()
        cbm.CHKOUT(WRITE_IO_CHANNEL)
        bool status=false

        if cbm.READST()==0 {
            %asm {{
                ; write 2 bytes prg header
                lda  skip_header
                bne  +
                lda  startaddress
                jsr  cbm.CHROUT
                lda  startaddress+1
                jsr  cbm.CHROUT
+               lda  startaddress
                ldy  startaddress+1
                sta  P8ZP_SCRATCH_PTR
                sty  P8ZP_SCRATCH_PTR+1

                jsr  cbm.READST
                bne  _error

                ; decrease savesize by 1 for the loop
                lda  savesize
                bne  +
                dec  savesize+1
+               dec  savesize

                ldy  #0
_loop:
                lda  (P8ZP_SCRATCH_PTR),y
                jsr  cbm.CHROUT

+               lda  savesize
                bne  +
                lda  savesize+1
                beq  _done
                dec  savesize+1
+               dec  savesize
                iny
                bne  _loop
                jsr  cbm.READST
                bne  _error
                inc  P8ZP_SCRATCH_PTR+1
                jmp  _loop
_done:
                inc  status
_error:
            }}
        }

        cbm.CLOSE(WRITE_IO_CHANNEL)
        cbm.CLRCHN()        ; restore default i/o devices
        return status
    }

    ; like save() but omits the 2 byte prg header.
    sub save_raw(str filenameptr, uword startaddress, uword savesize) -> bool {
        diskio.save.filenameptr = filenameptr
        diskio.save.startaddress = startaddress
        diskio.save.savesize = savesize
        diskio.save.skip_header = true
        goto diskio.save.internal_save
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

    sub get_loadaddress(str filename) -> uword {
        ; get the load adress from a PRG file (usually $0801 but it can be different)

        cbm.SETNAM(strings.length(filename), filename)
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        void cbm.OPEN()          ; open 12,8,0,"filename"
        void cbm.CHKIN(READ_IO_CHANNEL)
        cx16.r0L = cbm.CHRIN()
        cx16.r0H = cbm.CHRIN()
        if cbm.READST()!=0
            cx16.r0 = 0
        cbm.CLRCHN()
        cbm.CLOSE(READ_IO_CHANNEL)
        return cx16.r0
    }

    sub exists(str filename) -> bool {
        ; -- returns true if the given file exists on the disk, otherwise false
        ;    DON'T use this if you already have a file open with f_open!
        ;    NOTE: may not work correctly for empty files. Try to avoid empty files on CBM DOS systems.
        ;    NOTE: doesn't clear the dos error status and message, you'll have to read/clear that yourself (with status() for example)
        cbm.SETNAM(strings.length(filename), filename)
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        void cbm.OPEN()          ; open 12,8,0,"filename"
        void cbm.CHKIN(READ_IO_CHANNEL)
        void cbm.CHRIN()
        cx16.r0bL = cbm.READST()==0
        cbm.CLRCHN()
        cbm.CLOSE(READ_IO_CHANNEL)
        return cx16.r0bL
    }

    sub delete(str filenameptr) {
        ; -- delete a file on the drive
        list_filename[0] = 's'
        list_filename[1] = ':'
        ubyte flen = strings.copy(filenameptr, &list_filename+2)
        cbm.SETNAM(flen+2, list_filename)
        cbm.SETLFS(1, drivenumber, 15)
        void cbm.OPEN()
        cbm.CLRCHN()
        cbm.CLOSE(1)
    }

    sub rename(str oldfileptr, str newfileptr) {
        ; -- rename a file on the drive
        list_filename[0] = 'r'
        list_filename[1] = ':'
        ubyte flen_new = strings.copy(newfileptr, &list_filename+2)
        list_filename[flen_new+2] = '='
        ubyte flen_old = strings.copy(oldfileptr, &list_filename+3+flen_new)
        cbm.SETNAM(3+flen_new+flen_old, list_filename)
        cbm.SETLFS(1, drivenumber, 15)
        void cbm.OPEN()
        cbm.CLRCHN()
        cbm.CLOSE(1)
    }

    sub send_command(str commandptr) {
        ; -- send a dos command to the drive
        cbm.SETNAM(strings.length(commandptr), commandptr)
        cbm.SETLFS(15, drivenumber, 15)
        void cbm.OPEN()
        cbm.CLRCHN()
        cbm.CLOSE(15)
    }
}
