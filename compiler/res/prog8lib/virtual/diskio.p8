; File I/O routines for the VM target
;
; NOTE: not all is implemented.
; NOTE: some calls are slightly different from the "official" diskio library because for example,
;       here we cannot deal with multiple return values.

%import textio
%import syslib

diskio {
    %option no_symbol_prefixing, ignore_unused

    sub directory() -> bool {
        ; -- Prints the directory contents to the screen. Returns success.
        %ir {{
            syscall 45 (): r99100.b
            returnr.b r99100
        }}
    }

    ; the VM version of diskio has no facility to iterate over filenames.
    ; (list_filenames, lf_start_list, lf_next_entry, lf_end_list)


    ; ----- iterative file loader functions (uses the input io channel) -----

    sub f_open(str filenameptr) -> bool {
        ; -- open a file for iterative reading with f_read
        ;    note: only a single iteration loop can be active at a time!
        ;    Returns true if the file is successfully opened and readable.
        ;    No need to check status(), unlike f_open_w() !
        ;    NOTE: the default input isn't yet set to this logical file, you must use reset_read_channel() to do this,
        ;          if you're going to read from it yourself instead of using f_read()!

        %ir {{
            loadm.w r99000,diskio.f_open.filenameptr
            syscall 52 (r99000.w): r99100.b
            returnr.b r99100
        }}
    }

    sub f_read(str bufferpointer, uword num_bytes) -> uword {
        ; -- read from the currently open file, up to the given number of bytes.
        ;    returns the actual number of bytes read.  (checks for End-of-file and error conditions)
        uword actual
        repeat num_bytes {
            %ir {{
                syscall 54 (): r99000.w
                storem.w r99000,$ff02
            }}
            if cx16.r0H==0
                return actual
            @(bufferpointer) = cx16.r0L
            bufferpointer++
            actual++
        }
        return actual
    }

    sub f_read_all(str bufferpointer) -> uword {
        ; -- read the full rest of the file, returns number of bytes read.
        ;    It is assumed the file size is less than 64 K.
        ;    Usually you will just be using load() / load_raw() to read entire files!
        uword actual
        repeat {
            %ir {{
                syscall 54 (): r99000.w
                storem.w r99000,$ff02
            }}
            if cx16.r0H==0
                return actual
            @(bufferpointer) = cx16.r0L
            bufferpointer++
            actual++
        }
    }

    sub f_readline(str bufptr) -> ubyte {
        ; Routine to read text lines from a text file. Lines must be less than 255 characters.
        ; Reads characters from the input file UNTIL a newline or return character, or 0 byte (likely EOF).
        ; The line read will be 0-terminated in the buffer (and not contain the end of line character).
        ; The length of the line is returned. Note that an empty line is okay and is length 0!
        ; The success status is returned in the Carry flag instead: C set = success, C clear = failure/endoffile
        ubyte size
        repeat {
            %ir {{
                syscall 54 (): r99000.w
                storem.w r99000,$ff02
            }}

            if cx16.r0H==0 {
                sys.clear_carry()
                return size
            } else {
                if cx16.r0L in ['\n', '\r', 0] {
                    @(bufptr) = 0
                    sys.set_carry()
                    return size
                }
                @(bufptr) = cx16.r0L
                bufptr++
                size++
                if_z {
                    @(bufptr) = 0
                    return 255
                }
            }
        }
    }

    sub f_close() {
        ; -- end an iterative file loading session (close channels).
        ;    it is safe to call this multiple times, or when no file is open for reading.
        %ir {{
            syscall 56 ()
        }}
    }


    ; ----- iterative file writing functions (uses write io channel) -----

    sub f_open_w(str filenameptr) -> bool {
        ; -- open a file for iterative writing with f_write
        ;    WARNING: returns true if the open command was received by the device,
        ;    but this can still mean the file wasn't successfully opened for writing!
        ;    (for example, if it already exists). This is different than f_open()!
        ;    To be 100% sure if this call was successful, you have to use status()
        ;    and check the drive's status message!
        %ir {{
            loadm.w r99000,diskio.f_open_w.filenameptr
            syscall 53 (r99000.w): r99100.b
            returnr.b r99100
        }}
    }

    sub f_write(str bufferpointer, uword num_bytes) -> bool {
        ; -- write the given number of bytes to the currently open file
        ;    you can call this multiple times to append more data
        repeat num_bytes {
            %ir {{
                loadm.w r99000,diskio.f_write.bufferpointer
                loadi.b r99100,r99000
                syscall 55 (r99100.b): r99100.b
                storem.b r99100,$ff02
            }}
            if cx16.r0L==0
                return false
            bufferpointer++
        }
        return true
    }

    sub f_close_w() {
        ; -- end an iterative file writing session (close channels).
        ;    it is safe to call this multiple times, or when no file is open for reading.
        %ir {{
            syscall 57 ()
        }}
    }


    ; ---- other functions ----

    sub chdir(str path) {
        ; -- change current directory.
        %ir {{
            loadm.w r99000,diskio.chdir.path
            syscall 50 (r99000.w)
        }}
    }

    sub mkdir(str name) {
        ; -- make a new subdirectory.
        %ir {{
            loadm.w r99000,diskio.mkdir.name
            syscall 49 (r99000.w)
        }}
    }

    sub rmdir(str name) {
        ; -- remove a subdirectory.
        %ir {{
            loadm.w r99000,diskio.rmdir.name
            syscall 51 (r99000.w)
        }}
    }

    sub curdir() -> str {
        ; return current directory name or 0 if error
        %ir {{
            syscall 48 (): r99000.w
            returnr.w r99000
        }}
    }

    sub status() -> str {
        ; -- retrieve the disk drive's current status message
        return "unknown"
    }

    sub status_code() -> ubyte {
        ; -- return status code instead of whole CBM-DOS status string. (in this case always 255, which means 'unable to return sensible value')
        return 255
    }


    sub save(str filenameptr, uword start_address, uword savesize) -> bool {
        %ir {{
            load.b r99100,0
            loadm.w r99000,diskio.save.filenameptr
            loadm.w r99001,diskio.save.start_address
            loadm.w r99002,diskio.save.savesize
            syscall 42 (r99100.b, r99000.w, r99001.w, r99002.w): r99100.b
            returnr.b r99100
        }}
    }

    ; like save() but omits the 2 byte prg header.
    sub save_raw(str filenameptr, uword start_address, uword savesize) -> bool {
        %ir {{
            load.b r99100,1
            loadm.w r99000,diskio.save_raw.filenameptr
            loadm.w r99001,diskio.save_raw.start_address
            loadm.w r99002,diskio.save_raw.savesize
            syscall 42 (r99100.b, r99000.w, r99001.w, r99002.w): r99100.b
            returnr.b r99100
        }}
    }

    ; Use kernal LOAD routine to load the given program file in memory.
    ; This is similar to Basic's  LOAD "filename",drive  /  LOAD "filename",drive,1
    ; If you don't give an address_override, the location in memory is taken from the 2-byte file header.
    ; If you specify a custom address_override, the first 2 bytes in the file are ignored
    ; and the rest is loaded at the given location in memory.
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    sub load(str filenameptr, uword address_override) -> uword {
        %ir {{
            loadm.w r99000,diskio.load.filenameptr
            loadm.w r99001,diskio.load.address_override
            syscall 40 (r99000.w, r99001.w): r99002.w
            returnr.w r99002
        }}
    }

    ; Identical to load(), but DOES INCLUDE the first 2 bytes in the file.
    ; No program header is assumed in the file. Everything is loaded.
    ; See comments on load() for more details.
    sub load_raw(str filenameptr, uword start_address) -> uword {
        %ir {{
            loadm.w r99000,diskio.load_raw.filenameptr
            loadm.w r99001,diskio.load_raw.start_address
            syscall 41 (r99000.w, r99001.w): r99002.w
            returnr.w r99002
        }}
    }

    sub delete(str filenameptr) {
        ; -- delete a file on the drive
        %ir {{
            loadm.w r99000,diskio.delete.filenameptr
            syscall 43 (r99000.w)
        }}
    }

    sub rename(str oldfileptr, str newfileptr) {
        ; -- rename a file on the drive
        %ir {{
            loadm.w r99000,diskio.rename.oldfileptr
            loadm.w r99001,diskio.rename.newfileptr
            syscall 44 (r99000.w, r99001.w)
        }}
    }

    sub exists(str filename) -> bool {
        ; -- returns true if the given file exists on the disk, otherwise false
        if f_open(filename) {
            f_close()
            return true
        }
        return false
    }

    sub get_loadaddress(str filename) -> uword {
        ; get the load adress from a PRG file (usually $0801 but it can be different)
        if f_open(filename) {
            uword address
            void f_read(&address, 2)
            f_close()
            return address
        }
        return 0
    }
}
