; shared CBM (C64/C128) disk drive I/O routines.

; About the Dos/Drive error status message:
; The routines don't usually read/clear the dos/drive error status and message. ("blinking red led")
; In case of an error, you usually have to read/clear that yourself (with status() for example).


%import textio
%import conv
%import strings
%import syslib

diskio {
    %option merge, no_symbol_prefixing, ignore_unused

    const ubyte READ_IO_CHANNEL=12
    const ubyte WRITE_IO_CHANNEL=13
    const ubyte STATUS_EOF=$40

    ubyte @shared drivenumber = 8           ; user programs can set this to the drive number they want to load/save to!

    sub reset_read_channel() {
        cbm.CLRCHN()
        void cbm.CHKIN(READ_IO_CHANNEL)
    }

    sub reset_write_channel() {
        cbm.CLRCHN()
        cbm.CHKOUT(WRITE_IO_CHANNEL)
    }

    sub directory() -> bool {
        ; -- Prints the directory contents to the screen. Returns success.

        cbm.SETNAM(1, "$")
internal_dir:
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        ubyte status = 1
        void cbm.OPEN()          ; open 12,8,0,"$"
        if_cs
            goto io_error
        reset_read_channel()

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

    sub directory_dirs() -> bool {
        ; -- Prints all entries on the disk to the screen, but only directories.  Returns success.
        cbm.SETNAM(5, "$:*=c")      ; on C64 (1581 diskdrive) the type for directories is CBM
        goto diskio.directory.internal_dir
    }

    sub directory_files() -> bool {
        ; -- Prints all entries on the disk to the screen, but only actual files.  Returns success.
        cbm.SETNAM(5, "$:*=p")
        goto diskio.directory.internal_dir
    }


    sub diskname() -> str {
        ; -- Returns pointer to disk name string or 0 if failure.

        cbm.SETNAM(1, "$")
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        bool okay = false
        void cbm.OPEN()          ; open 12,8,0,"$"
        if_cs
            goto io_error
        reset_read_channel()

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

    ; internal variables for the iterative file lister / loader
    bool list_skip_disk_name
    ^^ubyte list_pattern
    uword list_blocks
    bool iteration_in_progress = false
    bool write_iteration_in_progress = false
    str list_filetype = "???"       ; prg, seq, dir
    str list_filename = "?" * 50

    ; ----- get a list of files (uses iteration functions internally) -----

    sub list_filenames(str pattern_ptr, uword filenames_buffer, uword filenames_buf_size) -> ubyte {
        ; -- fill the provided buffer with the names of the files on the disk (until buffer is full).
        ;    Files in the buffer are separated by a 0 byte. You can provide an optional pattern to match against.
        ;    After the last filename one additional 0 byte is placed to indicate the end of the list.
        ;    Returns number of files (it skips 'dir' entries i.e. subdirectories).
        ;    Also sets carry on exit: Carry clear = all files returned, Carry set = directory has more files that didn't fit in the buffer.
        ;    Note that no list of pointers of some form is returned, the names are just squashed together.
        ;    If you really need a list of pointers to the names, that is pretty straightforward to construct by iterating over the names
        ;    and registering when the next one starts after the 0-byte separator.
        uword buffer_start = filenames_buffer
        ubyte files_found = 0
        if lf_start_list(pattern_ptr) {
            while lf_next_entry() {
                if list_filetype!="dir" {
                    filenames_buffer += strings.copy(list_filename, filenames_buffer) + 1
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

    ; ----- iterative file lister functions (uses the read io channel) -----

    sub lf_start_list(str pattern_ptr) -> bool {
        ; -- start an iterative file listing with optional pattern matching.
        ;    note: only a single iteration loop can be active at a time!
        cbm.SETNAM(1, "$")

start_list_internal:
        lf_end_list()
        list_pattern = pattern_ptr
        list_skip_disk_name = true
        iteration_in_progress = true

        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        void cbm.OPEN()          ; open 12,8,0,"$"
        if_cs
            goto io_error
        reset_read_channel()

        repeat 4 {
            void cbm.CHRIN()     ; skip the 4 prologue bytes
        }

        if cbm.READST()==0
            return true

io_error:
        cbm.CLOSE(READ_IO_CHANNEL)
        lf_end_list()
        return false
    }

    sub lf_start_list_dirs(str pattern_ptr) -> bool {
        ; -- start an iterative directory contents listing with optional pattern matching.
        ;    this version it only returns directory entries!
        ;    note: only a single iteration loop can be active at a time!
        cbm.SETNAM(5, "$:*=c")  ; on C64 (1581 diskdrive) the type for directories is CBM
        diskio.lf_start_list.pattern_ptr = pattern_ptr
        goto diskio.lf_start_list.start_list_internal
    }

    sub lf_start_list_files(str pattern_ptr) -> bool {
        ; -- start an iterative directory contents listing with optional pattern matching.
        ;    this version only returns actual file entries!
        ;    note: only a single iteration loop can be active at a time!
        cbm.SETNAM(5, "$:*=p")
        diskio.lf_start_list.pattern_ptr = pattern_ptr
        goto diskio.lf_start_list.start_list_internal
    }

    sub lf_start_list_having_prefix(str prefix) -> bool {
        ; -- start an iterative directory contents listing for entries with given prefix.
        ;    note: only a single iteration loop can be active at a time!
        list_filename[0] = '$'
        list_filename[1] = ':'
        cx16.r0L = strings.copy(prefix, &list_filename+2)
        list_filename[cx16.r0L+2] = '*'
        list_filename[cx16.r0L+3] = 0
        cbm.SETNAM(cx16.r0L+3, list_filename)
        diskio.lf_start_list.pattern_ptr = 0
        goto diskio.lf_start_list.start_list_internal
    }

    sub lf_next_entry() -> bool {
        ; -- retrieve the next entry from an iterative file listing session.
        ;    results will be found in list_blocks, list_filename, and list_filetype.
        ;    if it returns false though, there are no more entries (or an error occurred).

        if not iteration_in_progress
            return false

        repeat {
            reset_read_channel()        ; use the input io channel again

            ^^ubyte nameptr = &list_filename
            ubyte blocks_lsb = cbm.CHRIN()
            ubyte blocks_msb = cbm.CHRIN()

            if cbm.READST()!=0
                goto close_end

            list_blocks = mkword(blocks_msb, blocks_lsb)

            ; read until the filename starts after the first "
            while cbm.CHRIN()!='\"'  {
                if cbm.READST()!=0
                    goto close_end
            }

            ; read the filename
            repeat {
                ubyte character = cbm.CHRIN()
                if character==0
                    break
                if character=='\"'
                    break
                @(nameptr) = character
                nameptr++
            }

            @(nameptr) = 0

            do {
                cx16.r15L = cbm.CHRIN()
            } until cx16.r15L!=' '      ; skip blanks up to 3 chars entry type
            list_filetype[0] = cx16.r15L
            list_filetype[1] = cbm.CHRIN()
            list_filetype[2] = cbm.CHRIN()
            while cbm.CHRIN()!=0 {
                ; read the rest of the entry until the end
            }

            void cbm.CHRIN()     ; skip 2 bytes
            void cbm.CHRIN()

            if not list_skip_disk_name {
                if list_pattern==0
                    return true
                if strings.pattern_match(list_filename, list_pattern)
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
            cbm.CLOSE(READ_IO_CHANNEL)
            iteration_in_progress = false
        }
    }


    ; ----- iterative file loader functions (uses the input io channel) -----

    sub f_open(str filenameptr) -> bool {
        ; -- open a file for iterative reading with f_read
        ;    note: only a single iteration loop can be active at a time!
        ;    Returns true if the file is successfully opened and readable.
        ;    NOTE: the default input isn't yet set to this logical file, you must use reset_read_channel() to do this,
        ;          if you're going to read from it yourself instead of using f_read()!
        f_close()

        cbm.SETNAM(strings.length(filenameptr), filenameptr)
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, READ_IO_CHANNEL)     ; note: has to be Channel,x,Channel because otherwise f_seek doesn't work
        void cbm.OPEN()          ; open 12,8,12,"filename"
        if_cc {
            ; check command channel for success status
            if status_code()==0 {
                iteration_in_progress = true
                return true
            }
        }
        f_close()
        cbm.CLOSE(READ_IO_CHANNEL)
        return false
    }

    sub f_read(uword bufferpointer, uword num_bytes) -> uword {
        ; -- read from the currently open file, up to the given number of bytes.
        ;    returns the actual number of bytes read.  (checks for End-of-file and error conditions)
        if not iteration_in_progress or num_bytes==0
            return 0

        reset_read_channel()
        if num_bytes==1 {
            ; slightly optimized path for reading just a single byte
            @(bufferpointer) = cbm.CHRIN()
            cx16.r0L = cbm.READST()
            cbm.CLRCHN()            ; reset default i/o channels
            if cx16.r0L!=0 {
                f_close()
                if cx16.r0L & STATUS_EOF == 0
                    return 0
            }
            return 1
        }

        list_blocks = 0     ; we reuse this variable for the total number of bytes read
        %asm {{
            lda  bufferpointer
            sta  P8ZP_SCRATCH_W1
            lda  bufferpointer+1
            sta  P8ZP_SCRATCH_W1+1
        }}
        while num_bytes!=0 {
            %asm {{
                jsr  cbm.CHRIN
                ldy  #0
                sta  (P8ZP_SCRATCH_W1),y
                inc  P8ZP_SCRATCH_W1
                bne  +
                inc  P8ZP_SCRATCH_W1+1
+
            }}
            list_blocks++
            cx16.r0L = cbm.READST()
            if_nz {
                f_close()
                cbm.CLRCHN()            ;  reset default i/o channels
                if cx16.r0L & STATUS_EOF !=0    ; eof?
                    return list_blocks   ; number of bytes read
                return 0  ; error.
            }
            num_bytes--
        }
        cbm.CLRCHN()            ; reset default i/o channels
        return list_blocks  ; number of bytes read
    }

    sub f_read_all(uword bufferpointer) -> uword {
        ; -- read the full rest of the file, returns number of bytes read.
        ;    It is assumed the file size is less than 64 K.
        ;    Usually you will just be using load() / load_raw() to read entire files!
        if not iteration_in_progress
            return 0

        reset_read_channel()
        uword total_read = 0
        while cbm.READST()==0 {
            cx16.r0 = f_read(bufferpointer, 256)
            if cx16.r0==0
                break
            total_read += cx16.r0
            bufferpointer += cx16.r0
        }
        cbm.CLRCHN()            ; reset default i/o channels
        return total_read
    }

    asmsub f_readline(^^ubyte bufptr @AY) clobbers(X) -> ubyte @Y, ubyte @A {
        ; Routine to read a text line from a text file. Lines must be less than 255 characters.
        ; Reads characters from the input file UNTIL a newline or return character, or 0 byte (likely EOF).
        ; The line read will be 0-terminated in the buffer (and not contain the end of line character).
        ; The length of the line is returned in Y. Note that an empty line is okay and is length 0!
        ; I/O error status should be checked by the caller itself via READST() routine.
        ; The I/O error status byte is returned in A.
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            jsr  reset_read_channel
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
_end        jsr  cbm.READST
            pha
            tya
            pha
            jsr  cbm.CLRCHN
            pla
            tay
            pla
            rts
        }}
    }

    sub f_close() {
        ; -- end an iterative file loading session (close channels).
        ;    it is safe to call this multiple times, or when no file is open for reading.
        if iteration_in_progress {
            cbm.CLRCHN()
            cbm.CLOSE(READ_IO_CHANNEL)
            iteration_in_progress = false
        }
    }


    ; ----- iterative file writing functions (uses write io channel) -----

    sub f_open_w(str filenameptr) -> bool {
        ; -- open a file for iterative writing with f_write
        ;    WARNING: returns true if the open command was received by the device,
        ;    but this can still mean the file wasn't successfully opened for writing!
        ;    (for example, if it already exists). This is different than f_open()!
        ;    NOTE: the default output isn't yet set to this file, you must use reset_write_channel() to do this,
        ;          if you're going to write to it yourself instead of using f_write()!
        f_close_w()

        cbm.SETNAM(strings.length(filenameptr), filenameptr)
        cbm.SETLFS(WRITE_IO_CHANNEL, drivenumber, 1)
        void cbm.OPEN()             ; open 13,8,1,"filename"
        if_cc {
            ; check command channel for success status
            if status_code()==0 {
                write_iteration_in_progress = true
                return true
            }
        }
        cbm.CLOSE(WRITE_IO_CHANNEL)
        f_close_w()
        return false
    }

    sub f_write(uword bufferpointer, uword num_bytes) -> bool {
        ; -- write the given number of bytes to the currently open file
        ;    you can call this multiple times to append more data
        if num_bytes!=0 {
            reset_write_channel()
            repeat num_bytes {
                cbm.CHROUT(@(bufferpointer))
                bufferpointer++
            }
            cx16.r0L = cbm.READST()
            cbm.CLRCHN()            ; reset default i/o channels
            return cx16.r0L==0
        }
        return true
    }

    sub f_close_w() {
        ; -- end an iterative file writing session (close channels).
        ;    it is safe to call this multiple times, or when no file is open for reading.
        if write_iteration_in_progress {
            cbm.CLRCHN()
            cbm.CLOSE(WRITE_IO_CHANNEL)
            write_iteration_in_progress = false
        }
    }


    ; ---- other functions ----

    sub status() -> str {
        ; -- retrieve the disk drive's current status message

; TODO this doesn't seem to work reliably, sometimes READST returns 128 when the drive is just fine
;        str device_not_present_error = "device not present #xx"
;        if cbm.READST()==128 {
;            device_not_present_error[len(device_not_present_error)-2] = 0
;            void strings.copy(conv.str_ub(drivenumber), &device_not_present_error+len(device_not_present_error)-2)
;            return device_not_present_error
;        }

        ^^ubyte messageptr = &list_filename
        cbm.SETNAM(0, list_filename)
        cbm.SETLFS(15, drivenumber, 15)
        void cbm.OPEN()          ; open 15,8,15
        if_cs
            goto io_error
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

io_error:
        void strings.copy("io error", list_filename)
        goto done
    }

    ; similar to above, but instead of fetching the entire string, it only fetches the status code and returns it as ubyte
    ; in case of IO error, returns 255 (CBM-DOS itself is physically unable to return such a value)
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

    sub save(str filenameptr, uword start_address, uword savesize) -> bool {
        cbm.SETNAM(strings.length(filenameptr), filenameptr)
        cbm.SETLFS(1, drivenumber, 0)
        uword @shared end_address = start_address + savesize
        cx16.r0L = 0

        %asm {{
            lda  start_address
            sta  P8ZP_SCRATCH_W1
            lda  start_address+1
            sta  P8ZP_SCRATCH_W1+1
            lda  #<P8ZP_SCRATCH_W1
            ldx  end_address
            ldy  end_address+1
            jsr  cbm.SAVE
            php
            plp
        }}

        if_cc
            cx16.r0L = cbm.READST()==0 as ubyte

        return cx16.r0L as bool
    }

    ; Use kernal LOAD routine to load the given program file in memory.
    ; This is similar to Basic's  LOAD "filename",drive  /  LOAD "filename",drive,1
    ; If you don't give an address_override, the location in memory is taken from the 2-byte file header.
    ; If you specify a custom address_override, the first 2 bytes in the file are ignored
    ; and the rest is loaded at the given location in memory.
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    sub load(str filenameptr, uword address_override) -> uword {
        cbm.SETNAM(strings.length(filenameptr), filenameptr)
        ubyte secondary = 1
        cx16.r1 = 0
        if address_override!=0
            secondary = 0
        cbm.SETLFS(1, drivenumber, secondary)
        %asm {{
            lda  #0
            ldx  address_override
            ldy  address_override+1
            jsr  cbm.LOAD
            bcs  +
            stx  cx16.r1
            sty  cx16.r1+1
+
        }}

        return cx16.r1
    }

    ; Identical to load(), but DOES INCLUDE the first 2 bytes in the file.
    ; No program header is assumed in the file. Everything is loaded.
    ; See comments on load() for more details.
    sub load_raw(str filenameptr, uword start_address) -> uword {
        ; read the 2 header bytes separately to skip them
        if not f_open(filenameptr)
            return 0
        cx16.r1 = f_read(start_address, 2)
        f_close()
        if cx16.r1!=2
            return 0
        start_address += 2
        return load(filenameptr, start_address)
    }

    ; Load a prog8 compiled library binary blob at the given location into memory.
    sub loadlib(str libnameptr, uword libaddress) -> uword {
        return load(libnameptr, libaddress)
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

    sub exists(str filename) -> bool {
        ; -- returns true if the given file exists on the disk, otherwise false
        ;    DON'T use this if you already have a file open with f_open!
        if f_open(filename) {
            f_close()
            return true
        }
        return false
    }

    sub send_command(str commandptr) {
        ; -- send a dos command to the drive
        cbm.SETNAM(strings.length(commandptr), commandptr)
        cbm.SETLFS(15, drivenumber, 15)
        void cbm.OPEN()
        cbm.CLRCHN()
        cbm.CLOSE(15)
    }

    sub get_loadaddress(str filename) -> uword {
        ; get the load adress from a PRG file (usually $0801 but it can be different)

        cbm.SETNAM(strings.length(filename), filename)
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, READ_IO_CHANNEL)
        void cbm.OPEN()          ; open 12,8,12,"filename"
        cx16.r0 = 0
        if_cc {
            void cbm.CHKIN(READ_IO_CHANNEL)
            cx16.r0L = cbm.CHRIN()
            cx16.r0H = cbm.CHRIN()
            if cbm.READST()!=0
                cx16.r0 = 0
        }
        cbm.CLRCHN()
        cbm.CLOSE(READ_IO_CHANNEL)
        return cx16.r0
    }
}
