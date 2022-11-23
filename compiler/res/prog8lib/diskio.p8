; C64 and Cx16 disk drive I/O routines.

%import textio
%import string
%import syslib

diskio {

    sub directory(ubyte drivenumber) -> bool {
        ; -- Prints the directory contents of disk drive 8-11 to the screen. Returns success.

        c64.SETNAM(1, "$")
        c64.SETLFS(12, drivenumber, 0)
        ubyte status = 1
        void c64.OPEN()          ; open 12,8,0,"$"
        if_cs
            goto io_error
        void c64.CHKIN(12)        ; use #12 as input channel
        if_cs
            goto io_error

        repeat 4 {
            void c64.CHRIN()     ; skip the 4 prologue bytes
        }

        ; while not key pressed / EOF encountered, read data.
        status = c64.READST()
        if status!=0 {
            status = 1
            goto io_error
        }

        while status==0 {
            ubyte low = c64.CHRIN()
            ubyte high = c64.CHRIN()
            txt.print_uw(mkword(high, low))
            txt.spc()
            ubyte @zp char
            repeat {
                char = c64.CHRIN()
                if char==0
                    break
                txt.chrout(char)
            }
            txt.nl()
            void c64.CHRIN()     ; skip 2 bytes
            void c64.CHRIN()
            status = c64.READST()
            if c64.STOP2()
                break
        }
        status = c64.READST()

io_error:
        c64.CLRCHN()        ; restore default i/o devices
        c64.CLOSE(12)

        if status and status & $40 == 0 {            ; bit 6=end of file
            txt.print("\ni/o error, status: ")
            txt.print_ub(status)
            txt.nl()
            return false
        }

        return true
    }

    sub diskname(ubyte drivenumber) -> uword {
        ; -- Returns pointer to disk name string or 0 if failure.

        c64.SETNAM(1, "$")
        c64.SETLFS(12, drivenumber, 0)
        ubyte okay = false
        void c64.OPEN()          ; open 12,8,0,"$"
        if_cs
            goto io_error
        void c64.CHKIN(12)        ; use #12 as input channel
        if_cs
            goto io_error

        repeat 6 {
            void c64.CHRIN()     ; skip the 6 prologue bytes
        }
        if c64.READST()!=0
            goto io_error

        ; while not key pressed / EOF encountered, read data.
        cx16.r0 = &list_filename
        repeat {
            @(cx16.r0) = c64.CHRIN()
            if @(cx16.r0)==0
                break
            cx16.r0++
        }
        okay = true

io_error:
        c64.CLRCHN()        ; restore default i/o devices
        c64.CLOSE(12)
        if okay
            return &list_filename
        return 0
    }

    ; internal variables for the iterative file lister / loader
    bool list_skip_disk_name
    uword list_pattern
    uword list_blocks
    bool iteration_in_progress = false
    ubyte @zp first_byte
    bool have_first_byte
    ubyte last_drivenumber = 8       ; which drive was last used for a f_open operation?
    str list_filetype = "???"       ; prg, seq, dir
    str list_filename = "?" * 50

    ; ----- get a list of files (uses iteration functions internally) -----

    sub list_filenames(ubyte drivenumber, uword pattern_ptr, uword filenames_buffer, uword filenames_buf_size) -> ubyte {
        ; -- fill the provided buffer with the names of the files on the disk (until buffer is full).
        ;    Files in the buffer are separeted by a 0 byte. You can provide an optional pattern to match against.
        ;    After the last filename one additional 0 byte is placed to indicate the end of the list.
        ;    Returns number of files (it skips 'dir' entries i.e. subdirectories).
        ;    Also sets carry on exit: Carry clear = all files returned, Carry set = directory has more files that didn't fit in the buffer.
        uword buffer_start = filenames_buffer
        ubyte files_found = 0
        if lf_start_list(drivenumber, pattern_ptr) {
            while lf_next_entry() {
                if list_filetype!="dir" {
                    filenames_buffer += string.copy(diskio.list_filename, filenames_buffer) + 1
                    files_found++
                    if filenames_buffer - buffer_start > filenames_buf_size-20 {
                        @(filenames_buffer)=0
                        lf_end_list()
                        sys.set_carry()
                        return files_found
                    }
                }
            }
            lf_end_list()
        }
        @(filenames_buffer)=0
        sys.clear_carry()
        return files_found
    }

    ; ----- iterative file lister functions (uses io channel 12) -----

    sub lf_start_list(ubyte drivenumber, uword pattern_ptr) -> bool {
        ; -- start an iterative file listing with optional pattern matching.
        ;    note: only a single iteration loop can be active at a time!
        lf_end_list()
        list_pattern = pattern_ptr
        list_skip_disk_name = true
        iteration_in_progress = true

        c64.SETNAM(1, "$")
        c64.SETLFS(12, drivenumber, 0)
        void c64.OPEN()          ; open 12,8,0,"$"
        if_cs
            goto io_error
        void c64.CHKIN(12)        ; use #12 as input channel
        if_cs
            goto io_error

        repeat 4 {
            void c64.CHRIN()     ; skip the 4 prologue bytes
        }

        if c64.READST()==0
            return true

io_error:
        lf_end_list()
        return false
    }

    sub lf_next_entry() -> bool {
        ; -- retrieve the next entry from an iterative file listing session.
        ;    results will be found in list_blocks, list_filename, and list_filetype.
        ;    if it returns false though, there are no more entries (or an error occurred).

        if not iteration_in_progress
            return false

        repeat {
            void c64.CHKIN(12)        ; use #12 as input channel again

            uword nameptr = &list_filename
            ubyte blocks_lsb = c64.CHRIN()
            ubyte blocks_msb = c64.CHRIN()

            if c64.READST()
                goto close_end

            list_blocks = mkword(blocks_msb, blocks_lsb)

            ; read until the filename starts after the first "
            while c64.CHRIN()!='\"'  {
                if c64.READST()
                    goto close_end
            }

            ; read the filename
            repeat {
                ubyte char = c64.CHRIN()
                if char==0
                    break
                if char=='\"'
                    break
                @(nameptr) = char
                nameptr++
            }

            @(nameptr) = 0

            do {
                cx16.r15L = c64.CHRIN()
            } until cx16.r15L!=' '      ; skip blanks up to 3 chars entry type
            list_filetype[0] = cx16.r15L
            list_filetype[1] = c64.CHRIN()
            list_filetype[2] = c64.CHRIN()
            while c64.CHRIN() {
                ; read the rest of the entry until the end
            }

            void c64.CHRIN()     ; skip 2 bytes
            void c64.CHRIN()

            if not list_skip_disk_name {
                if not list_pattern
                    return true
                if string.pattern_match(list_filename, list_pattern)
                    return true
            }
            list_skip_disk_name = false
        }

close_end:
        lf_end_list()
        return false
    }

    sub lf_end_list() {
        ; -- end an iterative file listing session (close channels).
        if iteration_in_progress {
            c64.CLRCHN()
            c64.CLOSE(12)
            iteration_in_progress = false
        }
    }


    ; ----- iterative file loader functions (uses io channel 12) -----

    sub f_open(ubyte drivenumber, uword filenameptr) -> bool {
        ; -- open a file for iterative reading with f_read
        ;    note: only a single iteration loop can be active at a time!
        f_close()

        c64.SETNAM(string.length(filenameptr), filenameptr)
        c64.SETLFS(12, drivenumber, 12)     ; note: has to be 12,x,12 because otherwise f_seek doesn't work
        last_drivenumber = drivenumber
        void c64.OPEN()          ; open 12,8,12,"filename"
        if_cc {
            if c64.READST()==0 {
                iteration_in_progress = true
                have_first_byte = false
                void c64.CHKIN(12)        ; use #12 as input channel
                if_cc {
                    first_byte = c64.CHRIN()   ; read first byte to test for file not found
                    if not c64.READST() {
                        have_first_byte = true
                        return true
                    }
                }
            }
        }
        f_close()
        return false
    }

    sub f_read(uword bufferpointer, uword num_bytes) -> uword {
        ; -- read from the currently open file, up to the given number of bytes.
        ;    returns the actual number of bytes read.  (checks for End-of-file and error conditions)
        ;    NOTE: on systems with banked ram (such as Commander X16) this routine DOES NOT
        ;          automatically load into subsequent banks if it reaches a bank boundary!
        if not iteration_in_progress or not num_bytes
            return 0

        list_blocks = 0     ; we reuse this variable for the total number of bytes read
        if have_first_byte {
            have_first_byte=false
            @(bufferpointer) = first_byte
            bufferpointer++
            list_blocks++
            num_bytes--
        }

        void c64.CHKIN(12)        ; use #12 as input channel again

        %asm {{
            lda  bufferpointer
            sta  m_in_buffer+1
            lda  bufferpointer+1
            sta  m_in_buffer+2
        }}
        repeat num_bytes {
            %asm {{
                jsr  c64.CHRIN
                sta  cx16.r5
m_in_buffer     sta  $ffff
                inc  m_in_buffer+1
                bne  +
                inc  m_in_buffer+2
+               inc  list_blocks
                bne  +
                inc  list_blocks+1
+
            }}

            if cx16.r5==$0d {   ; chance on I/o error status?
                first_byte = c64.READST()
                if first_byte & $40 {
                    f_close()       ; end of file, close it
                    list_blocks--   ; don't count that last CHRIN read
                }
                if first_byte
                    return list_blocks  ; number of bytes read
            }
        }
        return list_blocks  ; number of bytes read
    }

    sub f_read_all(uword bufferpointer) -> uword {
        ; -- read the full contents of the file, returns number of bytes read.
        if not iteration_in_progress
            return 0

        uword total_read = 0
        if have_first_byte {
            have_first_byte=false
            @(bufferpointer) = first_byte
            bufferpointer++
            total_read = 1
        }

        while not c64.READST() {
            uword size = f_read(bufferpointer, 256)
            total_read += size
            bufferpointer += size
        }
        return total_read
    }

    asmsub f_readline(uword bufptr @AY) clobbers(X) -> ubyte @Y {
        ; Routine to read text lines from a text file. Lines must be less than 255 characters.
        ; Reads characters from the input file UNTIL a newline or return character (or EOF).
        ; The line read will be 0-terminated in the buffer (and not contain the end of line character).
        ; The length of the line is returned in Y. Note that an empty line is okay and is length 0!
        ; I/O error status should be checked by the caller itself via READST() routine.
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldx  #12
            jsr  c64.CHKIN              ; use channel 12 again for input
            ldy  #0
            lda  have_first_byte
            beq  _loop
            lda  #0
            sta  have_first_byte
            lda  first_byte
            sta  (P8ZP_SCRATCH_W1),y
            iny
_loop       jsr  c64.CHRIN
            sta  (P8ZP_SCRATCH_W1),y
            beq  _end
            iny
            cmp  #$0a
            beq  _line_end
            cmp  #$0d
            bne  _loop
_line_end   dey     ; get rid of the trailing end-of-line char
            lda  #0
            sta  (P8ZP_SCRATCH_W1),y
_end        rts
        }}
    }


    sub f_close() {
        ; -- end an iterative file loading session (close channels).
        if iteration_in_progress {
            c64.CLRCHN()
            c64.CLOSE(12)
            iteration_in_progress = false
        }
    }


    ; ----- iterative file writing functions (uses io channel 13) -----

    sub f_open_w(ubyte drivenumber, uword filenameptr) -> bool {
        ; -- open a file for iterative writing with f_write
        f_close_w()

        c64.SETNAM(string.length(filenameptr), filenameptr)
        c64.SETLFS(13, drivenumber, 1)
        void c64.OPEN()             ; open 13,8,1,"filename"
        if_cc {
            c64.CHKOUT(13)          ; use #13 as output channel
            return not c64.READST()
        }
        f_close_w()
        return false
    }

    sub f_write(uword bufferpointer, uword num_bytes) -> bool {
        ; -- write the given number of bytes to the currently open file
        if num_bytes!=0 {
            c64.CHKOUT(13)        ; use #13 as output channel again
            repeat num_bytes {
                c64.CHROUT(@(bufferpointer))
                bufferpointer++
            }
            return not c64.READST()
        }
        return true
    }

    sub f_close_w() {
        ; -- end an iterative file writing session (close channels).
        c64.CLRCHN()
        c64.CLOSE(13)
    }


    ; ---- other functions ----

    sub status(ubyte drivenumber) -> uword {
        ; -- retrieve the disk drive's current status message
        uword messageptr = &list_filename
        c64.SETNAM(0, list_filename)
        c64.SETLFS(15, drivenumber, 15)
        void c64.OPEN()          ; open 15,8,15
        if_cs
            goto io_error
        void c64.CHKIN(15)        ; use #15 as input channel
        if_cs
            goto io_error

        while not c64.READST() {
            first_byte = c64.CHRIN()
            if first_byte=='\r' or first_byte=='\n'
                break
            @(messageptr) = first_byte
            messageptr++
        }
        @(messageptr) = 0

done:
        c64.CLRCHN()        ; restore default i/o devices
        c64.CLOSE(15)
        return list_filename

io_error:
        list_filename = "?disk error"
        goto done
    }

    sub save(ubyte drivenumber, uword filenameptr, uword address, uword size) -> bool {
        c64.SETNAM(string.length(filenameptr), filenameptr)
        c64.SETLFS(1, drivenumber, 0)
        uword @shared end_address = address + size
        first_byte = 0      ; result var reuse

        %asm {{
            lda  address
            sta  P8ZP_SCRATCH_W1
            lda  address+1
            sta  P8ZP_SCRATCH_W1+1
            stx  P8ZP_SCRATCH_REG
            lda  #<P8ZP_SCRATCH_W1
            ldx  end_address
            ldy  end_address+1
            jsr  c64.SAVE
            php
            ldx  P8ZP_SCRATCH_REG
            plp
        }}

        if_cc
            first_byte = c64.READST()==0

        c64.CLRCHN()
        c64.CLOSE(1)

        return first_byte
    }

    ; Use kernal LOAD routine to load the given program file in memory.
    ; This is similar to Basic's  LOAD "filename",drive  /  LOAD "filename",drive,1
    ; If you don't give an address_override, the location in memory is taken from the 2-byte file header.
    ; If you specify a custom address_override, the first 2 bytes in the file are ignored
    ; and the rest is loaded at the given location in memory.
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    ; NOTE: when the load is larger than 64Kb and/or spans multiple RAM banks
    ;       (which is possible on the Commander X16), the returned size is not correct,
    ;       because it doesn't take the number of ram banks into account.
    ;       Consider using cx16diskio.load() instead.
    sub load(ubyte drivenumber, uword filenameptr, uword address_override) -> uword {
        return load_headerless_cx16(drivenumber, filenameptr, address_override, false)
    }

    ; Use kernal LOAD routine to load the given file in memory.
    ; INCLUDING the first 2 bytes in the file: no program header is assumed in the file.
    ; This is different from Basic's LOAD instruction which always skips the first two bytes.
    ; The load address is mandatory.
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    ; NOTE: when the load is larger than 64Kb and/or spans multiple RAM banks
    ;       (which is possible on the Commander X16), the returned size is not correct,
    ;       because it doesn't take the number of ram banks into account.
    ;       Consider using cx16diskio.load_raw() instead on the Commander X16.
    sub load_raw(ubyte drivenumber, uword filenameptr, uword address) -> uword {
        if sys.target==16   ; are we on commander X16?
            return load_headerless_cx16(drivenumber, filenameptr, address, true)
        ; fallback to reading the 2 header bytes separately
        if not f_open(drivenumber, filenameptr)
            return 0
        cx16.r1 = f_read(address, 2)
        f_close()
        if cx16.r1!=2
            return 0
        address += 2
        return load(drivenumber, filenameptr, address)
    }


    ; Internal routine, only to be used on Commander X16 platform if headerless=true,
    ; because this routine uses kernal support for that to load headerless files.
    sub load_headerless_cx16(ubyte drivenumber, uword filenameptr, uword address_override, bool headerless) -> uword {
        c64.SETNAM(string.length(filenameptr), filenameptr)
        ubyte secondary = 1
        cx16.r1 = 0
        if address_override
            secondary = 0
        if headerless
            secondary |= %00000010  ; activate cx16 kernal headerless load support
        c64.SETLFS(1, drivenumber, secondary)
        %asm {{
            stx  P8ZP_SCRATCH_REG
            lda  #0
            ldx  address_override
            ldy  address_override+1
            jsr  c64.LOAD
            bcs  +
            stx  cx16.r1
            sty  cx16.r1+1
+           ldx  P8ZP_SCRATCH_REG
        }}

        c64.CLRCHN()
        c64.CLOSE(1)
        return cx16.r1
    }

    sub delete(ubyte drivenumber, uword filenameptr) {
        ; -- delete a file on the drive
        list_filename[0] = 's'
        list_filename[1] = ':'
        ubyte flen = string.copy(filenameptr, &list_filename+2)
        c64.SETNAM(flen+2, list_filename)
        c64.SETLFS(1, drivenumber, 15)
        void c64.OPEN()
        c64.CLRCHN()
        c64.CLOSE(1)
    }

    sub rename(ubyte drivenumber, uword oldfileptr, uword newfileptr) {
        ; -- rename a file on the drive
        list_filename[0] = 'r'
        list_filename[1] = ':'
        ubyte flen_new = string.copy(newfileptr, &list_filename+2)
        list_filename[flen_new+2] = '='
        ubyte flen_old = string.copy(oldfileptr, &list_filename+3+flen_new)
        c64.SETNAM(3+flen_new+flen_old, list_filename)
        c64.SETLFS(1, drivenumber, 15)
        void c64.OPEN()
        c64.CLRCHN()
        c64.CLOSE(1)
    }

    sub send_command(ubyte drivenumber, uword commandptr) {
        ; -- send a dos command to the drive
        c64.SETNAM(string.length(commandptr), commandptr)
        c64.SETLFS(15, drivenumber, 15)
        void c64.OPEN()
        c64.CLRCHN()
        c64.CLOSE(15)
    }
}
