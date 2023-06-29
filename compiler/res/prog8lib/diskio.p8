; C64/C128 disk drive I/O routines.

%import textio
%import string
%import syslib

diskio {
    %option no_symbol_prefixing

    ubyte drivenumber = 8

    sub set_drive(ubyte number) {
        drivenumber = number
    }

    sub directory() -> bool {
        ; -- Prints the directory contents to the screen. Returns success.

        cbm.SETNAM(1, "$")
        cbm.SETLFS(12, drivenumber, 0)
        ubyte status = 1
        void cbm.OPEN()          ; open 12,8,0,"$"
        if_cs
            goto io_error
        void cbm.CHKIN(12)        ; use #12 as input channel
        if_cs
            goto io_error

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
            ubyte @zp char
            repeat {
                char = cbm.CHRIN()
                if char==0
                    break
                txt.chrout(char)
            }
            txt.nl()
            void cbm.CHRIN()     ; skip 2 bytes
            void cbm.CHRIN()
            status = cbm.READST()
            if cbm.STOP2()
                break
        }
        status = cbm.READST()

io_error:
        cbm.CLRCHN()        ; restore default i/o devices
        cbm.CLOSE(12)

        if status and status & $40 == 0 {            ; bit 6=end of file
            txt.print("\ni/o error, status: ")
            txt.print_ub(status)
            txt.nl()
            return false
        }

        return true
    }

    sub diskname() -> uword {
        ; -- Returns pointer to disk name string or 0 if failure.

        cbm.SETNAM(1, "$")
        cbm.SETLFS(12, drivenumber, 0)
        ubyte okay = false
        void cbm.OPEN()          ; open 12,8,0,"$"
        if_cs
            goto io_error
        void cbm.CHKIN(12)        ; use #12 as input channel
        if_cs
            goto io_error

        while cbm.CHRIN()!='"' {
            ; skip up to entry name
        }
        if cbm.READST()!=0
            goto io_error

        cx16.r0 = &list_filename
        repeat {
            @(cx16.r0) = cbm.CHRIN()
            if @(cx16.r0)=='"' {
                @(cx16.r0) = ' '
                while @(cx16.r0)==' ' and cx16.r0>=&diskio.list_filename {
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
        cbm.CLOSE(12)
        if okay
            return &list_filename
        return 0
    }

    ; internal variables for the iterative file lister / loader
    bool list_skip_disk_name
    uword list_pattern
    uword list_blocks
    bool iteration_in_progress = false
    str list_filetype = "???"       ; prg, seq, dir
    str list_filename = "?" * 50

    ; ----- get a list of files (uses iteration functions internally) -----

    sub list_filenames(uword pattern_ptr, uword filenames_buffer, uword filenames_buf_size) -> ubyte {
        ; -- fill the provided buffer with the names of the files on the disk (until buffer is full).
        ;    Files in the buffer are separeted by a 0 byte. You can provide an optional pattern to match against.
        ;    After the last filename one additional 0 byte is placed to indicate the end of the list.
        ;    Returns number of files (it skips 'dir' entries i.e. subdirectories).
        ;    Also sets carry on exit: Carry clear = all files returned, Carry set = directory has more files that didn't fit in the buffer.
        uword buffer_start = filenames_buffer
        ubyte files_found = 0
        if lf_start_list(pattern_ptr) {
            while lf_next_entry() {
                if list_filetype!="dir" {
                    filenames_buffer += string.copy(list_filename, filenames_buffer) + 1
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

    sub lf_start_list(uword pattern_ptr) -> bool {
        ; -- start an iterative file listing with optional pattern matching.
        ;    note: only a single iteration loop can be active at a time!
        lf_end_list()
        list_pattern = pattern_ptr
        list_skip_disk_name = true
        iteration_in_progress = true

        cbm.SETNAM(1, "$")
        cbm.SETLFS(12, drivenumber, 0)
        void cbm.OPEN()          ; open 12,8,0,"$"
        if_cs
            goto io_error
        void cbm.CHKIN(12)        ; use #12 as input channel
        if_cs
            goto io_error

        repeat 4 {
            void cbm.CHRIN()     ; skip the 4 prologue bytes
        }

        if cbm.READST()==0
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
            void cbm.CHKIN(12)        ; use #12 as input channel again

            uword nameptr = &list_filename
            ubyte blocks_lsb = cbm.CHRIN()
            ubyte blocks_msb = cbm.CHRIN()

            if cbm.READST()
                goto close_end

            list_blocks = mkword(blocks_msb, blocks_lsb)

            ; read until the filename starts after the first "
            while cbm.CHRIN()!='\"'  {
                if cbm.READST()
                    goto close_end
            }

            ; read the filename
            repeat {
                ubyte char = cbm.CHRIN()
                if char==0
                    break
                if char=='\"'
                    break
                @(nameptr) = char
                nameptr++
            }

            @(nameptr) = 0

            do {
                cx16.r15L = cbm.CHRIN()
            } until cx16.r15L!=' '      ; skip blanks up to 3 chars entry type
            list_filetype[0] = cx16.r15L
            list_filetype[1] = cbm.CHRIN()
            list_filetype[2] = cbm.CHRIN()
            while cbm.CHRIN() {
                ; read the rest of the entry until the end
            }

            void cbm.CHRIN()     ; skip 2 bytes
            void cbm.CHRIN()

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
            cbm.CLRCHN()
            cbm.CLOSE(12)
            iteration_in_progress = false
        }
    }


    ; ----- iterative file loader functions (uses io channel 12) -----

    sub f_open(uword filenameptr) -> bool {
        ; -- open a file for iterative reading with f_read
        ;    note: only a single iteration loop can be active at a time!
        f_close()

        cbm.SETNAM(string.length(filenameptr), filenameptr)
        cbm.SETLFS(12, drivenumber, 12)     ; note: has to be 12,x,12 because otherwise f_seek doesn't work
        void cbm.OPEN()          ; open 12,8,12,"filename"
        if_cc {
            if cbm.READST()==0 {
                iteration_in_progress = true
                void cbm.CHKIN(12)          ; use #12 as input channel
                if_cc {
                    void cbm.CHRIN()        ; read first byte to test for file not found
                    if not cbm.READST() {
                        cbm.CLOSE(12)           ; close file because we already consumed first byte
                        void cbm.OPEN()         ; re-open the file
                        void cbm.CHKIN(12)
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
        if not iteration_in_progress or not num_bytes
            return 0

        list_blocks = 0     ; we reuse this variable for the total number of bytes read

        %asm {{
            lda  bufferpointer
            sta  m_in_buffer+1
            lda  bufferpointer+1
            sta  m_in_buffer+2
        }}
        while num_bytes {
            if cbm.READST() {
                f_close()
                if cbm.READST() & $40    ; eof?
                    return list_blocks   ; number of bytes read
                return 0  ; error.
            }
            %asm {{
                jsr  cbm.CHRIN
m_in_buffer     sta  $ffff
                inc  m_in_buffer+1
                bne  +
                inc  m_in_buffer+2
+
            }}
            list_blocks++
            num_bytes--
        }
        return list_blocks  ; number of bytes read
    }

    sub f_read_all(uword bufferpointer) -> uword {
        ; -- read the full contents of the file, returns number of bytes read.
        if not iteration_in_progress
            return 0

        uword total_read = 0
        while not cbm.READST() {
            cx16.r0 = f_read(bufferpointer, 256)
            total_read += cx16.r0
            bufferpointer += cx16.r0
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
            jsr  cbm.CHKIN              ; use channel 12 again for input
            ldy  #0
_loop       jsr  cbm.CHRIN
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
            cbm.CLRCHN()
            cbm.CLOSE(12)
            iteration_in_progress = false
        }
    }


    ; ----- iterative file writing functions (uses io channel 13) -----

    sub f_open_w(uword filenameptr) -> bool {
        ; -- open a file for iterative writing with f_write
        f_close_w()

        cbm.SETNAM(string.length(filenameptr), filenameptr)
        cbm.SETLFS(13, drivenumber, 1)
        void cbm.OPEN()             ; open 13,8,1,"filename"
        if_cc {
            cbm.CHKOUT(13)          ; use #13 as output channel
            return not cbm.READST()
        }
        f_close_w()
        return false
    }

    sub f_write(uword bufferpointer, uword num_bytes) -> bool {
        ; -- write the given number of bytes to the currently open file
        if num_bytes!=0 {
            cbm.CHKOUT(13)        ; use #13 as output channel again
            repeat num_bytes {
                cbm.CHROUT(@(bufferpointer))
                bufferpointer++
            }
            return not cbm.READST()
        }
        return true
    }

    sub f_close_w() {
        ; -- end an iterative file writing session (close channels).
        cbm.CLRCHN()
        cbm.CLOSE(13)
    }


    ; ---- other functions ----

    sub status() -> uword {
        ; -- retrieve the disk drive's current status message
        uword messageptr = &list_filename
        cbm.SETNAM(0, list_filename)
        cbm.SETLFS(15, drivenumber, 15)
        void cbm.OPEN()          ; open 15,8,15
        if_cs
            goto io_error
        void cbm.CHKIN(15)        ; use #15 as input channel
        if_cs
            goto io_error

        while not cbm.READST() {
            cx16.r5L = cbm.CHRIN()
            if cx16.r5L=='\r' or cx16.r5L=='\n'
                break
            @(messageptr) = cx16.r5L
            messageptr++
        }
        @(messageptr) = 0

done:
        cbm.CLRCHN()        ; restore default i/o devices
        cbm.CLOSE(15)
        return list_filename

io_error:
        list_filename = "?disk error"
        goto done
    }

    sub save(uword filenameptr, uword address, uword size) -> bool {
        cbm.SETNAM(string.length(filenameptr), filenameptr)
        cbm.SETLFS(1, drivenumber, 0)
        uword @shared end_address = address + size
        cx16.r0L = 0

        %asm {{
            lda  address
            sta  P8ZP_SCRATCH_W1
            lda  address+1
            sta  P8ZP_SCRATCH_W1+1
            stx  P8ZP_SCRATCH_REG
            lda  #<P8ZP_SCRATCH_W1
            ldx  end_address
            ldy  end_address+1
            jsr  cbm.SAVE
            php
            ldx  P8ZP_SCRATCH_REG
            plp
        }}

        if_cc
            cx16.r0L = cbm.READST()==0

        cbm.CLRCHN()
        cbm.CLOSE(1)

        return cx16.r0L
    }

    ; Use kernal LOAD routine to load the given program file in memory.
    ; This is similar to Basic's  LOAD "filename",drive  /  LOAD "filename",drive,1
    ; If you don't give an address_override, the location in memory is taken from the 2-byte file header.
    ; If you specify a custom address_override, the first 2 bytes in the file are ignored
    ; and the rest is loaded at the given location in memory.
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    sub load(uword filenameptr, uword address_override) -> uword {
        cbm.SETNAM(string.length(filenameptr), filenameptr)
        ubyte secondary = 1
        cx16.r1 = 0
        if address_override
            secondary = 0
        cbm.SETLFS(1, drivenumber, secondary)
        %asm {{
            stx  P8ZP_SCRATCH_REG
            lda  #0
            ldx  address_override
            ldy  address_override+1
            jsr  cbm.LOAD
            bcs  +
            stx  cx16.r1
            sty  cx16.r1+1
+           ldx  P8ZP_SCRATCH_REG
        }}

        cbm.CLRCHN()
        cbm.CLOSE(1)
        return cx16.r1
    }

    ; Identical to load(), but DOES INCLUDE the first 2 bytes in the file.
    ; No program header is assumed in the file. Everything is loaded.
    ; See comments on load() for more details.
    sub load_raw(uword filenameptr, uword address) -> uword {
        ; read the 2 header bytes separately to skip them
        if not f_open(filenameptr)
            return 0
        cx16.r1 = f_read(address, 2)
        f_close()
        if cx16.r1!=2
            return 0
        address += 2
        return load(filenameptr, address)
    }

    sub delete(uword filenameptr) {
        ; -- delete a file on the drive
        list_filename[0] = 's'
        list_filename[1] = ':'
        ubyte flen = string.copy(filenameptr, &list_filename+2)
        cbm.SETNAM(flen+2, list_filename)
        cbm.SETLFS(1, drivenumber, 15)
        void cbm.OPEN()
        cbm.CLRCHN()
        cbm.CLOSE(1)
    }

    sub rename(uword oldfileptr, uword newfileptr) {
        ; -- rename a file on the drive
        list_filename[0] = 'r'
        list_filename[1] = ':'
        ubyte flen_new = string.copy(newfileptr, &list_filename+2)
        list_filename[flen_new+2] = '='
        ubyte flen_old = string.copy(oldfileptr, &list_filename+3+flen_new)
        cbm.SETNAM(3+flen_new+flen_old, list_filename)
        cbm.SETLFS(1, drivenumber, 15)
        void cbm.OPEN()
        cbm.CLRCHN()
        cbm.CLOSE(1)
    }

    sub send_command(uword commandptr) {
        ; -- send a dos command to the drive
        cbm.SETNAM(string.length(commandptr), commandptr)
        cbm.SETLFS(15, drivenumber, 15)
        void cbm.OPEN()
        cbm.CLRCHN()
        cbm.CLOSE(15)
    }
}
