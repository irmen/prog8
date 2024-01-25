; Commander X16 disk drive I/O routines.
; Largely compatible with the default C64 ones, but adds more stuff specific to the X16 as well.

; NOTE: If you experience weird behavior with these routines and you are using them
;       in the X16 emulator using HostFs, please try again with an SD-card image instead first.
;       It is possible that there are still small differences between HostFS and actual CBM DOS in the emulator.
;
; About the secondary addresses:
; for writes (new files or overwrites), you can use 1 (without the mode string) or 2-14 (with the mode string)
; for reads (existing files) you can use 0 or 2-14 (mode string is optional)
; for modify mode (new files* or existing files), you must use 2-14, and the mode string ,s,m is required


%import textio
%import conv
%import string
%import syslib

diskio {
    %option no_symbol_prefixing, ignore_unused

    const ubyte READ_IO_CHANNEL=12
    const ubyte WRITE_IO_CHANNEL=13

    ubyte @shared drivenumber = 8           ; user programs can set this to the drive number they want to load/save to!

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
            if cbm.STOP2()!=0
                break
        }
        status = cbm.READST()

io_error:
        cbm.CLRCHN()        ; restore default i/o devices
        cbm.CLOSE(READ_IO_CHANNEL)

        if status!=0 and status & $40 == 0 {            ; bit 6=end of file
            txt.print("\ni/o error, status: ")
            txt.print_ub(status)
            txt.nl()
            return false
        }

        return true
    }

    sub diskname() -> uword {
        ; returns disk label name or 0 if error
        cbm.SETNAM(3, "$")
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        ubyte status = 1
        void cbm.OPEN()          ; open 12,8,0,"$=c"
        if_cs
            goto io_error
        reset_read_channel()

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
        status = cbm.READST()

io_error:
        cbm.CLRCHN()
        cbm.CLOSE(READ_IO_CHANNEL)
        if status!=0 and status & $40 == 0
            return 0
        return list_filename
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
        ;    Files in the buffer are separated by a 0 byte. You can provide an optional pattern to match against.
        ;    After the last filename one additional 0 byte is placed to indicate the end of the list.
        ;    Returns number of files (it skips 'dir' entries i.e. subdirectories).
        ;    Also sets carry on exit: Carry clear = all files returned, Carry set = directory has more files that didn't fit in the buffer.
        uword buffer_start = filenames_buffer
        ubyte files_found = 0
        filenames_buffer[0]=0
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

    ; ----- iterative file lister functions (uses the read io channel) -----

    sub lf_start_list(uword pattern_ptr) -> bool {
        ; -- start an iterative file listing with optional pattern matching.
        ;    note: only a single iteration loop can be active at a time!
        lf_end_list()
        list_pattern = pattern_ptr
        list_skip_disk_name = true
        iteration_in_progress = true

        cbm.SETNAM(1, "$")
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

    sub lf_next_entry() -> bool {
        ; -- retrieve the next entry from an iterative file listing session.
        ;    results will be found in list_blocks, list_filename, and list_filetype.
        ;    if it returns false though, there are no more entries (or an error occurred).

        if not iteration_in_progress
            return false

        repeat {
            reset_read_channel()        ; use the input io channel again

            uword nameptr = &list_filename
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
            cbm.CLOSE(READ_IO_CHANNEL)
            iteration_in_progress = false
        }
    }


    ; ----- iterative file loader functions (uses the read io channel) -----

    sub f_open(str filenameptr) -> bool {
        ; -- open a file for iterative reading with f_read
        ;    note: only a single iteration loop can be active at a time!
        ;    Returns true if the file is successfully opened and readable.
        ;    No need to check status(), unlike f_open_w() !
        ;    NOTE: the default input isn't yet set to this logical file, you must use reset_read_channel() to do this,
        ;          if you're going to read from it yourself instead of using f_read()!
        f_close()

        cbm.SETNAM(string.length(filenameptr), filenameptr)
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, READ_IO_CHANNEL)     ; note: has to be Channel,x,Channel because otherwise f_seek doesn't work
        void cbm.OPEN()          ; open 12,8,12,"filename"
        if_cc {
            reset_read_channel()
            if cbm.READST()==0 {
                iteration_in_progress = true
                void cbm.CHRIN()        ; read first byte to test for file not found
                if cbm.READST()==0 {
                    cbm.CLOSE(READ_IO_CHANNEL)    ; close file because we already consumed first byte
                    void cbm.OPEN()         ; re-open the file
                    cbm.CLRCHN()            ; reset default i/o channels
                    return true
                }
            }
        }
        f_close()
        cbm.CLOSE(READ_IO_CHANNEL)
        return false
    }

    ; optimized for Commander X16 to use MACPTR block read kernal call
    sub f_read(uword bufferpointer, uword num_bytes) -> uword {
        ; -- read from the currently open file, up to the given number of bytes.
        ;    returns the actual number of bytes read.  (checks for End-of-file and error conditions)
        ;    NOTE: cannot be used to load into VRAM.  Use vload() or call cx16.MACPTR() yourself with the vera data register as address.
        if not iteration_in_progress or num_bytes==0
            return 0

        reset_read_channel()
        list_blocks = 0     ; we reuse this variable for the total number of bytes read

        uword readsize
        while num_bytes!=0 {
            readsize = 255
            if num_bytes<readsize
                readsize = num_bytes
            readsize = cx16.MACPTR(lsb(readsize), bufferpointer, false)     ; fast block reads
            if_cs
                goto byte_read_loop     ; MACPTR block read not supported, do fallback loop
            list_blocks += readsize
            bufferpointer += readsize
            if msb(bufferpointer) == $c0
                bufferpointer = mkword($a0, lsb(bufferpointer))  ; wrap over bank boundary
            num_bytes -= readsize
            if cbm.READST() & $40 !=0 {
                f_close()       ; end of file, close it
                break
            }
        }
        return list_blocks  ; number of bytes read

byte_read_loop:         ; fallback if MACPTR isn't supported on the device
        %asm {{
            lda  bufferpointer
            sta  m_in_buffer+1
            lda  bufferpointer+1
            sta  m_in_buffer+2
        }}
        while num_bytes!=0 {
            if cbm.READST()!=0 {
                f_close()
                if cbm.READST() & $40 !=0    ; eof?
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

    ; optimized for Commander X16 to use MACPTR block read kernal call
    sub f_read_all(uword bufferpointer) -> uword {
        ; -- read the full contents of the file, returns number of bytes read.
        ;    NOTE: cannot be used to load into VRAM.  Use vload() or call cx16.MACPTR() yourself with the vera data register as address.
        if not iteration_in_progress
            return 0

        reset_read_channel()
        uword total_read = 0
        while cbm.READST()==0 {
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
_end        rts
        }}
    }

    sub f_close() {
        ; -- end an iterative file loading session (close channels).
        if iteration_in_progress {
            cbm.CLRCHN()
            cbm.CLOSE(READ_IO_CHANNEL)
            iteration_in_progress = false
        }
    }


    ; ----- iterative file writing functions (uses write io channel) -----

    sub f_open_w(str filename) -> bool {
        ; -- Open a file for iterative writing with f_write
        ;    WARNING: returns true if the open command was received by the device,
        ;    but this can still mean the file wasn't successfully opened for writing!
        ;    (for example, if it already exists). This is different than f_open()!
        ;    To be 100% sure if this call was successful, you have to use status()
        ;    and check the drive's status message!
        return internal_f_open_w(filename, false)
    }

    sub f_open_w_seek(str filename) -> bool {
        ; -- Open an existing file for iterative writing with f_write, and seeking with f_seek_w.
        return internal_f_open_w(filename, true)
    }

    sub internal_f_open_w(str filename, bool open_for_seeks) -> bool {
        f_close_w()
        list_filename = filename
        str modifier = ",s,?"
        modifier[3] = 'w'
        if open_for_seeks
            modifier[3] = 'm'
        cx16.r0L = string.append(list_filename, modifier)   ; secondary 13 requires a mode suffix to signal we're writing/modifying
        cbm.SETNAM(cx16.r0L, list_filename)
        cbm.SETLFS(WRITE_IO_CHANNEL, drivenumber, WRITE_IO_CHANNEL)
        void cbm.OPEN()             ; open 13,8,13,"filename"
        if_cc {
            return cbm.READST()==0
        }
        cbm.CLOSE(WRITE_IO_CHANNEL)
        f_close_w()
        return false
    }

    sub f_write(uword bufferpointer, uword num_bytes) -> bool {
        ; -- write the given number of bytes to the currently open file
        if num_bytes!=0 {
            reset_write_channel()
            do {
                cx16.r0 = cx16.MCIOUT(lsb(num_bytes), bufferpointer, false)     ; fast block writes
                if_cs
                    goto no_mciout
                num_bytes -= cx16.r0
                bufferpointer += cx16.r0
                if msb(bufferpointer) == $c0
                    bufferpointer = mkword($a0, lsb(bufferpointer))  ; wrap over bank boundary
                if cbm.READST()!=0
                    return false
            } until num_bytes==0
            return cbm.READST()==0

no_mciout:
            ; the device doesn't support MCIOUT, use a normal per-byte write loop
            repeat num_bytes {
                cbm.CHROUT(@(bufferpointer))
                bufferpointer++
            }
            return cbm.READST()==0
        }
        return true
    }

    sub f_close_w() {
        ; -- end an iterative file writing session (close channels).
        cbm.CLRCHN()
        cbm.CLOSE(WRITE_IO_CHANNEL)
    }


    ; ---- other functions ----

    sub status() -> uword {
        ; -- retrieve the disk drive's current status message
        str device_not_present_error = "device not present #xx"
        if cbm.READST()==128 {
            device_not_present_error[len(device_not_present_error)-2] = 0
            conv.str_ub(drivenumber)
            void string.copy(conv.string_out, &device_not_present_error+len(device_not_present_error)-2)
            return device_not_present_error
        }

        uword messageptr = &list_filename
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
        cbm.CLRCHN()        ; restore default i/o devices
        cbm.CLOSE(15)
        return list_filename

io_error:
        cbm.CLOSE(15)
        list_filename = "io error"
        goto done
    }


    ; saves a block of memory to disk, including the default 2 byte prg header.
    sub save(uword filenameptr, uword startaddress, uword savesize) -> bool {
        return internal_save_routine(filenameptr, startaddress, savesize, false)
    }

    ; like save() but omits the 2 byte prg header.
    sub save_raw(uword filenameptr, uword startaddress, uword savesize) -> bool {
        return internal_save_routine(filenameptr, startaddress, savesize, true)
    }

    sub internal_save_routine(uword filenameptr, uword startaddress, uword savesize, bool headerless) -> bool {
        cbm.SETNAM(string.length(filenameptr), filenameptr)
        cbm.SETLFS(1, drivenumber, 0)
        uword @shared end_address = startaddress + savesize
        cx16.r0L = 0

        %asm {{
            lda  startaddress
            sta  P8ZP_SCRATCH_W1
            lda  startaddress+1
            sta  P8ZP_SCRATCH_W1+1
            ldx  end_address
            ldy  end_address+1
            lda  headerless
            beq  +
            lda  #<P8ZP_SCRATCH_W1
            jsr  cx16.BSAVE
            bra  ++
+           lda  #<P8ZP_SCRATCH_W1
            jsr  cbm.SAVE
+           php
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
    ; NOTE: when the load is larger than 64Kb and/or spans multiple RAM banks
    ;       (which is possible on the Commander X16), the returned size is not correct,
    ;       because it doesn't take the number of ram banks into account.
    ;       You can use the load_size() function to calcuate the size in this case.
    ; NOTE: data is read into the current Ram bank if you're reading into banked ram.
    ;       if you require loading into another ram bank, you have to set that
    ;       yourself using cx16.rambank(bank) before calling load().
    sub load(uword filenameptr, uword address_override) -> uword {
        return internal_load_routine(filenameptr, address_override, false)
    }

    ; Identical to load(), but DOES INCLUDE the first 2 bytes in the file.
    ; No program header is assumed in the file. Everything is loaded.
    ; See comments on load() for more details.
    sub load_raw(uword filenameptr, uword startaddress) -> uword {
        return internal_load_routine(filenameptr, startaddress, true)
    }


    sub internal_load_routine(uword filenameptr, uword address_override, bool headerless) -> uword {
        cbm.SETNAM(string.length(filenameptr), filenameptr)
        ubyte secondary = 1
        cx16.r1 = 0
        if address_override!=0
            secondary = 0
        if headerless
            secondary |= %00000010  ; activate cx16 kernal headerless load support
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


    ; CommanderX16 extensions over the basic C64/C128 diskio routines:

    ; For use directly after a load or load_raw call (don't mess with the ram bank yet):
    ; Calculates the number of bytes loaded (files > 64Kb ar truncated to 16 bits)
    sub load_size(ubyte startbank, uword startaddress, uword endaddress) -> uword {
        return $2000 * (cx16.getrambank() - startbank) + endaddress - startaddress
    }

    asmsub vload(str name @R0, ubyte bank @A, uword startaddress @R1) clobbers(X, Y) -> ubyte @A {
        ; -- like the basic command VLOAD "filename",drivenumber,bank,address
        ;    loads a file into Vera's video memory in the given bank:address, returns success in A
        ;    the file has to have the usual 2 byte header (which will be skipped)
        %asm {{
            clc
internal_vload:
            pha
            ldx  drivenumber
            bcc +
            ldy  #%00000010     ; headerless load mode
            bne  ++
+           ldy  #0             ; normal load mode
+           lda  #1
            jsr  cbm.SETLFS
            lda  cx16.r0
            ldy  cx16.r0+1
            jsr  prog8_lib.strlen
            tya
            ldx  cx16.r0
            ldy  cx16.r0+1
            jsr  cbm.SETNAM
            pla
            clc
            adc  #2
            ldx  cx16.r1
            ldy  cx16.r1+1
            stz  P8ZP_SCRATCH_B1
            jsr  cbm.LOAD
            bcs  +
            inc  P8ZP_SCRATCH_B1
    +       jsr  cbm.CLRCHN
            lda  #1
            jsr  cbm.CLOSE
            lda  P8ZP_SCRATCH_B1
            rts
        }}
    }

    asmsub vload_raw(str name @R0, ubyte bank @A, uword startaddress @R1) clobbers(X, Y) -> ubyte @A {
        ; -- like the basic command BVLOAD "filename",drivenumber,bank,address
        ;    loads a file into Vera's video memory in the given bank:address, returns success in A
        ;    the file is read fully including the first two bytes.
        %asm {{
            sec
            jmp  vload.internal_vload
        }}
    }

    sub chdir(str path) {
        ; -- change current directory.
        list_filename[0] = 'c'
        list_filename[1] = 'd'
        list_filename[2] = ':'
        void string.copy(path, &list_filename+3)
        send_command(list_filename)
    }

    sub mkdir(str name) {
        ; -- make a new subdirectory.
        list_filename[0] = 'm'
        list_filename[1] = 'd'
        list_filename[2] = ':'
        void string.copy(name, &list_filename+3)
        send_command(list_filename)
    }

    sub rmdir(str name) {
        ; -- remove a subdirectory.
        void string.find(name, '*')
        if_cs
            return    ; refuse to act on a wildcard *
        list_filename[0] = 'r'
        list_filename[1] = 'd'
        list_filename[2] = ':'
        void string.copy(name, &list_filename+3)
        send_command(list_filename)
    }

    sub curdir() -> uword {
        ; return current directory name or 0 if error
        ; special X16 dos command to only return the current path in the entry list (R42+)
        const ubyte MAX_PATH_LEN=80
        uword reversebuffer = memory("curdir_buffer", MAX_PATH_LEN, 0)
        cx16.r12 = reversebuffer + MAX_PATH_LEN-1
        @(cx16.r12)=0
        cbm.SETNAM(3, "$=c")
        cbm.SETLFS(READ_IO_CHANNEL, drivenumber, 0)
        void cbm.OPEN()          ; open 12,8,0,"$=c"
        if_cs
            goto io_error
        reset_read_channel()

        repeat 6 {
            void cbm.CHRIN()
        }
        while cbm.CHRIN()!=0 {
            ; skip first line (drive label)
        }
        while cbm.CHRIN()!='"' {
            ; skip to first name
        }
        ubyte status = cbm.READST()
        cx16.r10 = &list_filename
        while status==0 {
            repeat {
                @(cx16.r10) = cbm.CHRIN()
                if @(cx16.r10)==0
                    break
                cx16.r10++
            }
            while @(cx16.r10)!='"' and cx16.r10>=&list_filename {
                @(cx16.r10)=0
                cx16.r10--
            }
            @(cx16.r10)=0
            prepend(list_filename)
            cx16.r10 = &list_filename
            while cbm.CHRIN()!='"' and status==0 {
                status = cbm.READST()
                ; skipping up to next entry name
            }
        }

io_error:
        cbm.CLRCHN()
        cbm.CLOSE(READ_IO_CHANNEL)
        if status!=0 and status & $40 == 0
            return 0
        if @(cx16.r12)==0 {
            cx16.r12--
            @(cx16.r12)='/'
        }
        return cx16.r12

        sub prepend(str dir) {
            if dir[0]=='/' and dir[1]==0
                return
            cx16.r9L = string.length(dir)
            cx16.r12 -= cx16.r9L
            sys.memcopy(dir, cx16.r12, cx16.r9L)
            cx16.r12--
            @(cx16.r12)='/'
        }
    }

    sub relabel(str name) {
        ; -- change the disk label.
        list_filename[0] = 'r'
        list_filename[1] = '-'
        list_filename[2] = 'h'
        list_filename[3] = ':'
        void string.copy(name, &list_filename+4)
        send_command(list_filename)
    }

    sub f_seek(uword pos_hiword, uword pos_loword) {
        ; -- seek in the reading file opened with f_open, to the given 32-bits position
        ubyte[6] command = ['p',0,0,0,0,0]
        command[1] = READ_IO_CHANNEL       ; f_open uses this secondary address
        command[2] = lsb(pos_loword)
        command[3] = msb(pos_loword)
        command[4] = lsb(pos_hiword)
        command[5] = msb(pos_hiword)
    send_command:
        cbm.SETNAM(sizeof(command), &command)
        cbm.SETLFS(15, drivenumber, 15)
        void cbm.OPEN()
        cbm.CLOSE(15)
        reset_read_channel()       ; back to the read io channel
    }


    sub f_seek_w(uword pos_hiword, uword pos_loword) {
        ; -- seek in the output file opened with f_open_w_seek, to the given 32-bits position
        diskio.f_seek.command[1] = WRITE_IO_CHANNEL       ; f_open_w uses this secondary address
        diskio.f_seek.command[2] = lsb(pos_loword)
        diskio.f_seek.command[3] = msb(pos_loword)
        diskio.f_seek.command[4] = lsb(pos_hiword)
        diskio.f_seek.command[5] = msb(pos_hiword)
        cbm.SETNAM(sizeof(diskio.f_seek.command), &diskio.f_seek.command)
        cbm.SETLFS(15, drivenumber, 15)
        void cbm.OPEN()
        cbm.CLOSE(15)
        reset_write_channel()    ; back to the write io channel
    }

}
