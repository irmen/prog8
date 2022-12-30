; Cx16 specific disk drive I/O routines.

%import diskio
%import string

cx16diskio {

    ; Same as diskio.load() but with additional bank parameter to select the Ram bank to load into.
    ; Use kernal LOAD routine to load the given program file in memory.
    ; This is similar to Basic's  LOAD "filename",drive  /  LOAD "filename",drive,1
    ; If you don't give an address_override, the location in memory is taken from the 2-byte file header.
    ; If you specify a custom address_override, the first 2 bytes in the file are ignored
    ; and the rest is loaded at the given location in memory.
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    ; You can use the load_size() function to calcuate the size of the file that was loaded.
    sub load(ubyte drivenumber, uword filenameptr, ubyte bank, uword address_override) -> uword {
        cx16.rambank(bank)
        return diskio.internal_load_routine(drivenumber, filenameptr, address_override, false)
    }

    ; Same as diskio.load_raw() but with additional bank parameter to select the Ram bank to load into.
    ; Use kernal LOAD routine to load the given file in memory.
    ; INCLUDING the first 2 bytes in the file: no program header is assumed in the file.
    ; The load address is mandatory. Returns the number of bytes loaded.
    ; If you load into regular system ram, use cx16.getrambank() for the bank argument,
    ; or alternatively make sure to reset the correct ram bank yourself after the load!
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    ; You can use the load_size() function to calcuate the size of the file that was loaded.
    sub load_raw(ubyte drivenumber, uword filenameptr, ubyte bank, uword address_override) -> uword {
        cx16.rambank(bank)
        return diskio.internal_load_routine(drivenumber, filenameptr, address_override, true)
    }

    ; For use directly after a load or load_raw call (don't mess with the ram bank yet):
    ; Calculates the number of bytes loaded (files > 64Kb ar truncated to 16 bits)
    sub load_size(ubyte startbank, uword startaddress, uword endaddress) -> uword {
        return $2000 * (cx16.getrambank() - startbank) + endaddress - startaddress
    }

    asmsub vload(str name @R0, ubyte drivenumber @Y, ubyte bank @A, uword address @R1) -> ubyte @A {
        ; -- like the basic command VLOAD "filename",drivenumber,bank,address
        ;    loads a file into Vera's video memory in the given bank:address, returns success in A
        ;    the file has to have the usual 2 byte header (which will be skipped)
        %asm {{
            clc
internal_vload:
            phx
            pha
            tya
            tax
            bcc +
            ldy  #%00000010     ; headerless load mode
            bne  ++
+           ldy  #0             ; normal load mode
+           lda  #1
            jsr  c64.SETLFS
            lda  cx16.r0
            ldy  cx16.r0+1
            jsr  prog8_lib.strlen
            tya
            ldx  cx16.r0
            ldy  cx16.r0+1
            jsr  c64.SETNAM
            pla
            clc
            adc  #2
            ldx  cx16.r1
            ldy  cx16.r1+1
            stz  P8ZP_SCRATCH_B1
            jsr  c64.LOAD
            bcs  +
            inc  P8ZP_SCRATCH_B1
    +       jsr  c64.CLRCHN
            lda  #1
            jsr  c64.CLOSE
            plx
            lda  P8ZP_SCRATCH_B1
            rts
        }}
    }

    asmsub vload_raw(str name @R0, ubyte drivenumber @Y, ubyte bank @A, uword address @R1) -> ubyte @A {
        ; -- like the basic command BVLOAD "filename",drivenumber,bank,address
        ;    loads a file into Vera's video memory in the given bank:address, returns success in A
        ;    the file is read fully including the first two bytes.
        %asm {{
            sec
            jmp  vload.internal_vload
        }}
    }

    ; Replacement function that makes use of fast block read capability of the X16,
    ; and can wrap over multiple ram banks while reading.
    ; Use this in place of regular diskio.f_read() on X16.
    sub f_read(uword bufferpointer, uword num_bytes) -> uword {
        ; -- read from the currently open file, up to the given number of bytes.
        ;    returns the actual number of bytes read.  (checks for End-of-file and error conditions)
        if not diskio.iteration_in_progress or not num_bytes
            return 0

        diskio.list_blocks = 0     ; we reuse this variable for the total number of bytes read

        ; commander X16 supports fast block-read via macptr() kernal call
        uword size
        while num_bytes {
            size = 255
            if num_bytes<size
                size = num_bytes
            size = cx16.macptr(lsb(size), bufferpointer, false)
            if_cs
                goto byte_read_loop     ; macptr block read not supported, do fallback loop
            diskio.list_blocks += size
            bufferpointer += size
            if msb(bufferpointer) == $c0
                bufferpointer = mkword($a0, lsb(bufferpointer))  ; wrap over bank boundary
            num_bytes -= size
            if c64.READST() & $40 {
                diskio.f_close()       ; end of file, close it
                break
            }
        }
        return diskio.list_blocks  ; number of bytes read

byte_read_loop:         ; fallback if macptr() isn't supported on the device
        %asm {{
            lda  bufferpointer
            sta  m_in_buffer+1
            lda  bufferpointer+1
            sta  m_in_buffer+2
        }}
        while num_bytes {
            if c64.READST() {
                diskio.f_close()
                if c64.READST() & $40    ; eof?
                    return diskio.list_blocks   ; number of bytes read
                return 0  ; error.
            }
            %asm {{
                jsr  c64.CHRIN
m_in_buffer     sta  $ffff
                inc  m_in_buffer+1
                bne  +
                inc  m_in_buffer+2
+
            }}
            diskio.list_blocks++
            num_bytes--
        }
        return diskio.list_blocks  ; number of bytes read
    }

    ; replacement function that makes use of fast block read capability of the X16
    ; use this in place of regular diskio.f_read_all() on X16
    sub f_read_all(uword bufferpointer) -> uword {
        ; -- read the full contents of the file, returns number of bytes read.
        if not diskio.iteration_in_progress
            return 0

        uword total_read = 0
        while not c64.READST() {
            cx16.r0 = cx16diskio.f_read(bufferpointer, 256)
            total_read += cx16.r0
            bufferpointer += cx16.r0
        }
        return total_read
    }


    sub chdir(ubyte drivenumber, str path) {
        ; -- change current directory.
        diskio.list_filename[0] = 'c'
        diskio.list_filename[1] = 'd'
        diskio.list_filename[2] = ':'
        void string.copy(path, &diskio.list_filename+3)
        diskio.send_command(drivenumber, diskio.list_filename)
    }

    sub mkdir(ubyte drivenumber, str name) {
        ; -- make a new subdirectory.
        diskio.list_filename[0] = 'm'
        diskio.list_filename[1] = 'd'
        diskio.list_filename[2] = ':'
        void string.copy(name, &diskio.list_filename+3)
        diskio.send_command(drivenumber, diskio.list_filename)
    }

    sub rmdir(ubyte drivenumber, str name) {
        ; -- remove a subdirectory.
        void string.find(name, '*')
        if_cs
            return    ; refuse to act on a wildcard *
        diskio.list_filename[0] = 'r'
        diskio.list_filename[1] = 'd'
        diskio.list_filename[2] = ':'
        void string.copy(name, &diskio.list_filename+3)
        diskio.send_command(drivenumber, diskio.list_filename)
    }

    sub relabel(ubyte drivenumber, str name) {
        ; -- change the disk label.
        diskio.list_filename[0] = 'r'
        diskio.list_filename[1] = '-'
        diskio.list_filename[2] = 'h'
        diskio.list_filename[3] = ':'
        void string.copy(name, &diskio.list_filename+4)
        diskio.send_command(drivenumber, diskio.list_filename)
    }

    sub f_seek(uword pos_hiword, uword pos_loword) {
        ; -- seek in the reading file opened with f_open, to the given 32-bits position
        ubyte[6] command = ['p',0,0,0,0,0]
        command[1] = 12       ; f_open uses channel 12
        command[2] = lsb(pos_loword)
        command[3] = msb(pos_loword)
        command[4] = lsb(pos_hiword)
        command[5] = msb(pos_hiword)
    send_command:
        c64.SETNAM(sizeof(command), &command)
        c64.SETLFS(15, diskio.last_drivenumber, 15)
        void c64.OPEN()
        c64.CLOSE(15)
    }

    ; TODO see if we can get this to work as well:
;    sub f_seek_w(uword pos_hiword, uword pos_loword) {
;        ; -- seek in the output file opened with f_open_w, to the given 32-bits position
;        cx16diskio.f_seek.command[1] = 13       ; f_open_w uses channel 13
;        cx16diskio.f_seek.command[2] = lsb(pos_loword)
;        cx16diskio.f_seek.command[3] = msb(pos_loword)
;        cx16diskio.f_seek.command[4] = lsb(pos_hiword)
;        cx16diskio.f_seek.command[5] = msb(pos_hiword)
;        goto cx16diskio.f_seek.send_command
;    }
}
