; Cx16 specific disk drive I/O routines.

%import diskio

cx16diskio {

    ; Use kernal LOAD routine to load the given program file in memory.
    ; This is similar to Basic's  LOAD "filename",drive  /  LOAD "filename",drive,1
    ; If you don't give an address_override, the location in memory is taken from the 2-byte file header.
    ; If you specify a custom address_override, the first 2 bytes in the file are ignored
    ; and the rest is loaded at the given location in memory.
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    ; You can use the load_size() function to calcuate the size of the file that was loaded.
    sub load(ubyte drivenumber, uword filenameptr, ubyte bank, uword address_override) -> uword {
        cx16.rambank(bank)
        return diskio.load(drivenumber, filenameptr, address_override)
    }

    ; Use kernal LOAD routine to load the given file in memory.
    ; INCLUDING the first 2 bytes in the file: no program header is assumed in the file.
    ; The load address is mandatory. Returns the number of bytes loaded.
    ; If you load into regular system ram, use cx16.getrambank() for the bank argument,
    ; or alternatively make sure to reset the correct ram bank yourself after the load!
    ; Returns the end load address+1 if successful or 0 if a load error occurred.
    ; You can use the load_size() function to calcuate the size of the file that was loaded.
    sub load_raw(ubyte drivenumber, uword filenameptr, ubyte bank, uword address) -> uword {
        cx16.rambank(bank)
        return diskio.load_headerless_cx16(drivenumber, filenameptr, address, true)
    }

    ; For use directly after a load or load_raw call (don't mess with the ram bank yet):
    ; Calculates the number of bytes loaded (files > 64Kb ar truncated to 16 bits)
    sub load_size(ubyte startbank, uword startaddress, uword endaddress) -> uword {
        return $2000 * (cx16.getrambank() - startbank) + endaddress - startaddress
    }

    asmsub vload(str name @R0, ubyte device @Y, ubyte bank @A, uword address @R1) -> ubyte @A {
        ; -- like the basic command VLOAD "filename",device,bank,address
        ;    loads a file into Vera's video memory in the given bank:address, returns success in A
        %asm {{
            ; -- load a file into video ram
            phx
            pha
            tya
            tax
            lda  #1
            ldy  #0
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


    ; replacement function that makes use of fast block read capability of the X16
    ; use this in place of regular diskio.f_read()
    sub f_read(uword bufferpointer, uword num_bytes) -> uword {
        ; -- read from the currently open file, up to the given number of bytes.
        ;    returns the actual number of bytes read.  (checks for End-of-file and error conditions)
        if not diskio.iteration_in_progress or not num_bytes
            return 0

        diskio.list_blocks = 0     ; we reuse this variable for the total number of bytes read
        if diskio.have_first_byte {
            diskio.have_first_byte=false
            @(bufferpointer) = diskio.first_byte
            bufferpointer++
            diskio.list_blocks++
            num_bytes--
        }

        void c64.CHKIN(11)        ; use #11 as input channel again

        ; commander X16 supports fast block-read via macptr() kernal call
        uword size
        while num_bytes {
            size = 255
            if num_bytes<size
                size = num_bytes
            size = cx16.macptr(lsb(size), bufferpointer)
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
        repeat num_bytes {
            %asm {{
                jsr  c64.CHRIN
                sta  cx16.r5
m_in_buffer     sta  $ffff
                inc  m_in_buffer+1
                bne  +
                inc  m_in_buffer+2
+               inc  diskio.list_blocks
                bne  +
                inc  diskio.list_blocks+1
+
            }}

            if cx16.r5==$0d {   ; chance on I/o error status?
                diskio.first_byte = c64.READST()
                if diskio.first_byte & $40 {
                    diskio.f_close()       ; end of file, close it
                    diskio.list_blocks--   ; don't count that last CHRIN read
                }
                if diskio.first_byte
                    return diskio.list_blocks  ; number of bytes read
            }
        }
        return diskio.list_blocks  ; number of bytes read
    }

    ; replacement function that makes use of fast block read capability of the X16
    ; use this in place of regular diskio.f_read_all()
    sub f_read_all(uword bufferpointer) -> uword {
        ; -- read the full contents of the file, returns number of bytes read.
        if not diskio.iteration_in_progress
            return 0

        uword total_read = 0
        if diskio.have_first_byte {
            diskio.have_first_byte=false
            @(bufferpointer) = diskio.first_byte
            bufferpointer++
            total_read = 1
        }

        while not c64.READST() {
            uword size = cx16diskio.f_read(bufferpointer, 256)
            total_read += size
            bufferpointer += size
        }
        return total_read
    }

}
