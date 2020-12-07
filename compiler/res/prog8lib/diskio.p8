%import textio
%import syslib

; Note: this code is compatible with C64 and CX16.

diskio {


    sub directory(ubyte drivenumber) -> ubyte {
        ; -- Shows the directory contents of disk drive 8-11 (provide as argument). Returns success flag.

        c64.SETNAM(1, "$")
        c64.SETLFS(1, drivenumber, 0)
        void c64.OPEN()          ; open 1,8,0,"$"
        if_cs
            goto io_error
        void c64.CHKIN(1)        ; use #1 as input channel
        if_cs
            goto io_error

        repeat 4 {
            void c64.CHRIN()     ; skip the 4 prologue bytes
        }

        ; while not key pressed / EOF encountered, read data.
        ubyte status = c64.READST()
        while not status {
            txt.print_uw(mkword(c64.CHRIN(), c64.CHRIN()))
            txt.chrout(' ')
            ubyte @zp char
            do {
                char = c64.CHRIN()
                txt.chrout(char)
            } until char==0
            txt.chrout('\n')
            void c64.CHRIN()     ; skip 2 bytes
            void c64.CHRIN()
            status = c64.READST()
            void c64.STOP()
            if_nz
                break
        }

io_error:
        status = c64.READST()
        c64.CLRCHN()        ; restore default i/o devices
        c64.CLOSE(1)

        if status and status != 64 {            ; 64=end of file
            txt.print("\ni/o error, status: ")
            txt.print_ub(status)
            txt.chrout('\n')
            return false
        }

        return true
    }


    sub listfiles(ubyte drivenumber, uword pattern, ubyte prefixOrSuffix,
                  uword filenamesbufferptr, uword blocksizesptr, ubyte max_files) -> ubyte {
        ; -- returns a list of files in the directory matching the given pattern (optional)
        ;    their blocksizes will be put into the uword array given by blocksizesptr
        ;    their names will be concatenated into the filenamesbuffer, separated by a 0-byte
        ;    The buffer should be at least 17 times max_files and the block sizes array should be big enough too.
        if max_files==0  return 0
        if pattern!=0 and strlen(pattern)==0  pattern=0

;        @(blocksizesptr) = lsb(333)
;        @(blocksizesptr+1) = msb(333)
;        @(blocksizesptr+2) = lsb(444)
;        @(blocksizesptr+3) = msb(444)
;        @(blocksizesptr+4) = lsb(555)
;        @(blocksizesptr+5) = msb(555)
;        str name1 = "filename1.txt"
;        str name2 = "filename2.txt"
;        str name3 = "filename3.txt"
;        memcopy(name1, filenamesbufferptr, len(name1)+1)
;        filenamesbufferptr += len(name1) + 1
;        memcopy(name2, filenamesbufferptr, len(name2)+1)
;        filenamesbufferptr += len(name2) + 1
;        memcopy(name3, filenamesbufferptr, len(name3)+1)
;        filenamesbufferptr += len(name3) + 1

        ubyte num_files = 0

        c64.SETNAM(1, "$")
        c64.SETLFS(1, drivenumber, 0)
        void c64.OPEN()          ; open 1,8,0,"$"
        if_cs
            goto io_error
        void c64.CHKIN(1)        ; use #1 as input channel
        if_cs
            goto io_error

        repeat 4 {
            void c64.CHRIN()     ; skip the 4 prologue bytes
        }

        while not c64.READST() {
            @(blocksizesptr) = c64.CHRIN()
            @(blocksizesptr+1) = c64.CHRIN()
            blocksizesptr += 2

            ; read until the filename starts after the first "
            while c64.CHRIN()!='\"'  {
                if c64.READST()
                    goto io_error
            }

            ubyte char
            do {
                char = c64.CHRIN()
                @(filenamesbufferptr) = char
                filenamesbufferptr++
            } until char==0
            num_files++
            void c64.CHRIN()     ; skip 2 bytes
            void c64.CHRIN()
        }

io_error:
        c64.CLRCHN()        ; restore default i/o devices
        c64.CLOSE(1)
        return num_files
    }

    sub status(ubyte drivenumber) {
        ; -- display the disk drive's current status message
        c64.SETNAM(0, $0000)
        c64.SETLFS(15, drivenumber, 15)
        void c64.OPEN()          ; open 15,8,15
        if_cs
            goto io_error
        void c64.CHKIN(15)        ; use #15 as input channel
        if_cs
            goto io_error

        while not c64.READST()
            txt.chrout(c64.CHRIN())

io_error:
        c64.CLRCHN()        ; restore default i/o devices
        c64.CLOSE(15)
    }


    sub save(ubyte drivenumber, uword filenameptr, uword address, uword size) -> ubyte {
        c64.SETNAM(strlen(filenameptr), filenameptr)
        c64.SETLFS(1, drivenumber, 0)
        uword end_address = address + size

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

        ubyte result=0
        if_cc
            result = c64.READST()==0

        c64.CLRCHN()
        c64.CLOSE(1)

        return result
    }

    sub load(ubyte drivenumber, uword filenameptr, uword address_override) -> uword {
        c64.SETNAM(strlen(filenameptr), filenameptr)
        ubyte secondary = 1
        uword end_of_load = 0
        if address_override
            secondary = 0
        c64.SETLFS(1, drivenumber, secondary)
        %asm {{
            stx  P8ZP_SCRATCH_REG
            lda  #0
            ldx  address_override
            ldy  address_override+1
            jsr  c64.LOAD
            bcs  +
            stx  end_of_load
            sty  end_of_load+1
+           ldx  P8ZP_SCRATCH_REG
        }}

        c64.CLRCHN()
        c64.CLOSE(1)

        if end_of_load
            return end_of_load - address_override

        return 0
    }


    str filename = "0:??????????????????????????????????????"

    sub delete(ubyte drivenumber, uword filenameptr) {
        ; -- delete a file on the drive
        ubyte flen = strlen(filenameptr)
        filename[0] = 's'
        filename[1] = ':'
        memcopy(filenameptr, &filename+2, flen+1)
        c64.SETNAM(flen+2, filename)
        c64.SETLFS(1, drivenumber, 15)
        void c64.OPEN()
        c64.CLRCHN()
        c64.CLOSE(1)
    }

    sub rename(ubyte drivenumber, uword oldfileptr, uword newfileptr) {
        ; -- rename a file on the drive
        ubyte flen_old = strlen(oldfileptr)
        ubyte flen_new = strlen(newfileptr)
        filename[0] = 'r'
        filename[1] = ':'
        memcopy(newfileptr, &filename+2, flen_new)
        filename[flen_new+2] = '='
        memcopy(oldfileptr, &filename+3+flen_new, flen_old+1)
        c64.SETNAM(3+flen_new+flen_old, filename)
        c64.SETLFS(1, drivenumber, 15)
        void c64.OPEN()
        c64.CLRCHN()
        c64.CLOSE(1)
    }
}
