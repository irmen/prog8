%import syslib
%import string

; Streaming routine for MCF files (multipurpose chunk format):
; 1. call open()
; 2. set callbacks if needed, set_callbacks()
; 3. call stream() in a loop
; 4. call close() if you want to cleanup halfway through for some reason


mcf {
    uword loadlist_buf = memory("loadlist", 256, 0)
    uword @zp loadlist_ptr
    bool needs_loadlist
    ubyte file_channel

    sub open(str filename, ubyte drive, ubyte channel) -> bool {
        cbm.SETNAM(string.length(filename), filename)
        cbm.SETLFS(channel, drive, 2)
        void cbm.OPEN()
        if_cc {
            if cbm.READST()==0 {
                void cbm.CHKIN(channel)
                loadlist_ptr = loadlist_buf
                needs_loadlist = true
                return true
            }
        }
        close()
        return false
    }

    sub close() {
        cbm.CLRCHN()
        cbm.CLOSE(file_channel)
    }

    asmsub set_callbacks(uword getbuffer_routine @R0, uword processchunk_routine @R1) {
        %asm {{
            lda  cx16.r0
            ldy  cx16.r0+1
            sta  p8_mcf.p8_stream.getbuffer_call+1
            sty  p8_mcf.p8_stream.getbuffer_call+2
            lda  cx16.r1
            ldy  cx16.r1+1
            sta  p8_mcf.p8_stream.processchunk_call+1
            sty  p8_mcf.p8_stream.processchunk_call+2
            rts
        }}
    }

    sub stream() {
        repeat {
            if needs_loadlist {
                if not read_loadlist() {
                    sys.set_carry()
                    return
                }
                needs_loadlist = false
            }

            while (loadlist_ptr-loadlist_buf)<256 {
                when @(loadlist_ptr) {
                    255 -> {
                        ; simply ignore this byte
                        loadlist_ptr++
                    }
                    254 -> {
                        ; End of File
                        close()
                        sys.set_carry()
                        return
                    }
                    253 -> {
                        ; pause streaming
                        cx16.r0 = peekw(loadlist_ptr+1)
                        loadlist_ptr+=6
                        sys.clear_carry()
                        return
                    }
                    252 -> {
                        ; load into vram
                        blockload_vram(peekw(loadlist_ptr+1), peek(loadlist_ptr+3), peekw(loadlist_ptr+4))
                        loadlist_ptr+=6
                    }
                    251 -> {
                        ; load into system ram
                        blockload_ram(peekw(loadlist_ptr+1), peek(loadlist_ptr+3), peekw(loadlist_ptr+4))
                        loadlist_ptr+=6
                    }
                    250 -> {
                        ; dummy chunk
                        blockload_dummy(peekw(loadlist_ptr+1))
                        loadlist_ptr+=6
                    }
                    else -> {
                        ; custom chunk
                        uword @shared chunksize = peekw(loadlist_ptr+1)
                        %asm {{
                            lda  (p8_mcf.p8_loadlist_ptr)
                            ldx  p8_chunksize
                            ldy  p8_chunksize+1
getbuffer_call              jsr  $ffff          ; modified
                            bcc   +
                            rts         ; fail - exit as if EOF
+                           sta  cx16.r1L
                            stx  cx16.r0
                            sty  cx16.r0+1
                        }}
                        blockload_ram(chunksize, cx16.r1L, cx16.r0)
                        loadlist_ptr += 6
                        %asm {{
processchunk_call           jsr  $ffff      ; modified
                            bcc  +
                            rts
+
                        }}
                    }
                }
            }
            needs_loadlist = true
        }
    }

    sub read_loadlist() -> bool {
        blockload_ram(256, cx16.getrambank(), loadlist_buf)
        if loadlist_buf[0]!=sc:'L' or loadlist_buf[1]!=sc:'L' or loadlist_buf[2]!=1
            return false        ; header error
        loadlist_ptr = loadlist_buf+4
    }

    sub blockload_vram(uword size, ubyte bank, uword address) {
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = lsb(address)
        cx16.VERA_ADDR_M = msb(address)
        cx16.VERA_ADDR_H = bank | %00010000     ; enable vera auto increment
        while size {
            size -= readblock(size, &cx16.VERA_DATA0, true)
        }
    }

    sub blockload_dummy(uword size) {
        ubyte buffer
        while size {
            size -= readblock(size, &buffer, true)
        }
    }

    sub blockload_ram(uword size, ubyte bank, uword address) {
        ubyte orig_ram_bank = cx16.getrambank()
        cx16.rambank(bank)
        cx16.r3 = address
        while size {
            cx16.r2 = readblock(size, cx16.r3, false)
            size -= cx16.r2
            cx16.r3 += cx16.r2
        }
        cx16.rambank(orig_ram_bank)
    }

    sub readblock(uword size, uword address, bool dontAdvance) -> uword {
        if msb(size)>=2
            return cx16.macptr(0, address, dontAdvance)         ; read 512 bytes
        if msb(size)
            return cx16.macptr(255, address, dontAdvance)       ; read 255 bytes
        return cx16.macptr(lsb(size), address, dontAdvance)     ; read remaining number of bytes
    }
}