; Routines to load and save "BMX" files (commander X16 bitmap format) Version 1.
; Bitmap data is loaded directly into VRAM without intermediary buffering.
; Only uncompressed images are supported for now.
; BMX Specification: https://cx16forum.com/forum/viewtopic.php?t=6945

%import diskio
%option ignore_unused

bmx {

    ubyte[32] header
    str FILEID = petscii:"bmx"

    ubyte bitsperpixel          ; consider using set_bpp() when setting this
    ubyte vera_colordepth       ; consider using set_vera_colordepth() when setting this
    uword width
    uword height
    ubyte border
    uword palette_entries       ; 1-256
    ubyte palette_start
    ubyte compression
    ^^ubyte @shared palette_buffer_ptr = 0    ; should you want to load or save the palette into main memory instead of directly into vram

    ^^ubyte error_message             ; pointer to error message, or 0 if all ok
    ubyte old_drivenumber

    sub open(ubyte drivenumber, str filename) -> bool {
        ; Open a BMX bitmap file and reads the header information.
        ; Returns true if all is ok, false otherwise + error_message will be set.
        error_message = 0
        old_drivenumber = diskio.drivenumber
        diskio.drivenumber = drivenumber
        if diskio.f_open(filename) {
            diskio.reset_read_channel()
            if read_header() {
                if parse_header() {
                    if palette_entries>0 {
                        if width<=640 {
                            return true
                        } else
                            error_message = "image too large"
                    } else
                        error_message = "invalid bmx file"
                } ; note: parse_header sets the error message by itself
            } else
                error_message = "invalid bmx file"
        } else
            error_message = "can't open file"

        close()
        return false
    }

    sub close() {
        ; if you want to close the file before actually loading palette or bitmap data.
        diskio.f_close()
        diskio.drivenumber = old_drivenumber
    }

    sub continue_load(ubyte vbank, uword vaddr) -> bool {
        ; Continues loading the palette and bitmap data from the opened BMX file.
        ; Parameters: the vram bank and address where the bitmap data should go.
        ; You can set palette_buffer_ptr if you want the palette buffered rather than directly into vram.
        ; Note: does not change vera screen mode or colordepth! You have to do that yourself!
        ; Returns true if all is ok, false otherwise + error_message will be set.
        error_message = 0
        diskio.reset_read_channel()
        if width==320 or width==640 {
            if compression==0 {
                if read_palette() {
                    if not read_bitmap(vbank, vaddr)
                        error_message = "bitmap error"
                } else
                    error_message = "palette error"
            } else
                error_message = "compression not supported"
        } else
            error_message = "width not 320 or 640"      ; note: use continue_load_stamp() to read other sizes

        close()
        return error_message==0
    }

    sub continue_load_stamp(ubyte vbank, uword vaddr, uword screenwidth) -> bool {
        ; Continues loading the palette and bitmap "stamp" data from the opened BMX file.
        ; "Stamp" means: load an image that is smaller than the screen (so we need to pad around it)
        ; Parameters:the vram bank and address where the bitmap data should go,
        ; and the screen width that the stamp image is loaded into.
        ; You can set palette_buffer_ptr if you want the palette buffered rather than directly into vram.
        ; Note: does not change vera screen mode or colordepth! You have to do that yourself!
        ; Returns true if all is ok, false otherwise + error_message will be set.
        error_message = 0
        diskio.reset_read_channel()
        if compression==0 {
            if read_palette() {
                if not read_bitmap_padded(vbank, vaddr, screenwidth)
                    error_message = "bitmap error"
            } else
                error_message = "palette error"
        } else
            error_message = "compression not supported"

        close()
        return error_message==0
    }

    sub continue_load_only_palette() -> bool {
        ; Continues loading the palette but not the bitmap data from the opened BMX file.
        ; You can set palette_buffer_ptr if you want the palette buffered rather than directly into vram.
        ; Returns true if all is ok, false otherwise + error_message will be set.
        ; Afterwards the file is closed and you can no longer read additional data from it!
        error_message = 0
        diskio.reset_read_channel()
        if not read_palette()
            error_message = "palette error"
        close()
        return error_message==0
    }

    sub save(ubyte drivenumber, str filename, ubyte vbank, uword vaddr, uword screenwidth) -> bool {
        ; Save bitmap and palette data from vram into a BMX file.
        ; First you must have set all bmx.* variables to the correct values! (like width, height..)
        ; Parameters:
        ; drive number and filename to save to,
        ; vram bank and address of the bitmap data to save,
        ; and optionally the screen width if you want to save a "stamp" that's smaller than the screen.
        ; If you're saving the whole screen width, you can leave screenwidth at 0.
        ; Returns: success status. If false, error_message points to the error message string.
        error_message = 0
        if compression!=0 {
            error_message = "compression not supported"
            return false
        }
        if screenwidth==0 {
            if width!=320 and width!=640 {
                error_message = "width not 320 or 640"
                return false
            }
        } else {
            if width>screenwidth {
                error_message = "image too large"
                return false
            }
        }

        old_drivenumber = diskio.drivenumber
        diskio.drivenumber = drivenumber
        if diskio.f_open_w(filename) {
            diskio.reset_write_channel()
            if write_header() {
                if write_palette() {
                    if not write_bitmap(vbank, vaddr, screenwidth)
                        error_message = "bitmap error"
                } else
                    error_message = "palette error"
            } else
                error_message = "header error"
        } else
             error_message = "can't open file"
save_end:
        diskio.f_close_w()
        diskio.drivenumber = old_drivenumber
        return error_message==0
    }

    sub set_bpp(ubyte bpp) {
        ubyte[8] depths = [0,1,1,2,2,2,2,3]
        vera_colordepth = depths[bpp-1]
        bitsperpixel = bpp
    }

    sub set_vera_colordepth(ubyte depth) {
        vera_colordepth = depth
        bitsperpixel = 1 << depth
    }

; ------------------- helper routines, usually not needed to call yourself -------------------------

    sub read_header() -> bool {
        ; load a BMX header from the currently active input file
        for cx16.r0L in 0 to sizeof(header)-1 {
            header[cx16.r0L] = cbm.CHRIN()
        }
        return cbm.READST()==0
    }

    sub read_palette() -> bool {
        ; load palette data from the currently active input file
        ; if palette_buffer_ptr is not 0, the palette data is read into that memory buffer,
        ; otherwise it is read directly into the palette in vram.
        cx16.vaddr(1, $fa00+palette_start*2, 0, 1)
        cx16.r3 = palette_buffer_ptr
        cx16.r2L = lsb(palette_entries)
        do {
            cx16.r4L = cbm.CHRIN()
            cx16.r4H = cbm.CHRIN()
            if cx16.r3!=0 {
                pokew(cx16.r3, cx16.r4)             ; into memory
                cx16.r3+=2
            } else {
                cx16.VERA_DATA0 = cx16.r4L          ; into vram
                cx16.VERA_DATA0 = cx16.r4H
            }
            cx16.r2L--
        } until cx16.r2L==0
        return cbm.READST()==0 or cbm.READST()&$40!=0    ; no error or eof?
    }

    sub read_bitmap_padded(ubyte vbank, uword vaddr, uword screenwidth) -> bool {
        ; load bitmap "stamp" into vram from the currently active input file
        cx16.r3 = bytes_per_scanline(width)         ; num bytes per image scanline
        cx16.r2 = bytes_per_scanline(screenwidth)   ; num bytes per screen scanline
        repeat height {
            cx16.vaddr(vbank, vaddr, 0, 1)
            read_scanline(cx16.r3)
            vaddr += cx16.r2
            if_cs
                vbank++
        }
        return cbm.READST()==0 or cbm.READST()&$40!=0    ; no error or eof?
    }

    sub read_bitmap(ubyte vbank, uword vaddr) -> bool {
        ; load contiguous bitmap into vram from the currently active input file
        cx16.r3 = bytes_per_scanline(width)
        cx16.vaddr(vbank, vaddr, 0, 1)
        repeat height
            read_scanline(cx16.r3)
        return cbm.READST()==0 or cbm.READST()&$40!=0    ; no error or eof?
    }

    sub read_scanline(uword size) {
        while size!=0 {
            void, cx16.r0 = cx16.MACPTR(min(255, size) as ubyte, &cx16.VERA_DATA0, true)
            if_cs {
                ; no MACPTR support
                repeat size
                    cx16.VERA_DATA0 = cbm.CHRIN()
                return
            }
            size -= cx16.r0
        }
    }

    sub write_header() -> bool {
        ; save out the BMX header to the currently active output file
        build_header()
        for cx16.r0L in 0 to sizeof(header)-1 {
            cbm.CHROUT(header[cx16.r0L])
        }
        return cbm.READST()==0
    }

    sub write_palette() -> bool {
        ; write palette data to the currently active input file
        ; if palette_buffer_ptr is not 0, the palette data is read from that memory buffer,
        ; otherwise it is read directly from the palette in vram.
        cx16.r3 = palette_buffer_ptr
        cx16.r2L = lsb(palette_entries)
        cx16.vaddr(1, $fa00+palette_start*2, 0, 1)
        do {
            if cx16.r3!=0 {
                cbm.CHROUT(@(cx16.r3))      ; from memory
                cx16.r3++
                cbm.CHROUT(@(cx16.r3))
                cx16.r3++
            } else {
                cbm.CHROUT(cx16.VERA_DATA0) ; from vram
                cbm.CHROUT(cx16.VERA_DATA0)
            }
            cx16.r2L--
        } until cx16.r2L==0
        return cbm.READST()==0
    }

    sub write_bitmap(ubyte vbank, uword vaddr, uword screenwidth) -> bool {
        ; screenwidth=0: save contiguous bitmap from vram to the currently active output file
        ; screenwidth>0: save "stamp" bitmap from vram to the currently active output file
        cx16.vaddr(vbank, vaddr, 0, 1)
        cx16.r3 = bytes_per_scanline(width)         ; num bytes per image scanline
        cx16.r2 = 0
        if screenwidth!=0
            cx16.r2 = bytes_per_scanline(screenwidth-width)   ; num bytes padding per screen scanline
        repeat height {
            write_scanline(cx16.r3)
            repeat cx16.r2
                cx16.r0L = cx16.VERA_DATA0
        }
        return cbm.READST()==0

        sub write_scanline(uword size) {
            while size!=0 {
                cx16.r0L = lsb(size)
                if msb(size)!=0
                    cx16.r0L = 0        ; 256 bytes
                void, cx16.r0 = cx16.MCIOUT(cx16.r0L, &cx16.VERA_DATA0, true)
                if_cs {
                    ; no MCIOUT support
                    repeat size
                        cbm.CHROUT(cx16.VERA_DATA0)
                    return
                }
                size -= cx16.r0
            }
        }
    }

    sub bytes_per_scanline(uword w) -> uword {
        ubyte[4] shifts = [3,2,1,0]
        return w >> shifts[vera_colordepth]
    }

    sub parse_header() -> bool {
        if header[0]==FILEID[0] and header[1]==FILEID[1] and header[2]==FILEID[2] {
            if header[3]==1 {       ; only version 1 supported for now
                bitsperpixel = header[4]
                vera_colordepth = header[5]
                width = peekw(&header+6)
                height = peekw(&header+8)
                palette_entries = header[10]
                if palette_entries==0
                    palette_entries=256
                palette_start = header[11]
                ; the data offset is not needed:  data_offset = peekw(&header+12)
                compression = header[14]
                border = header[15]
                return true
            } else
                error_message = "unsupported bmx file version"
        } else
            error_message = "invalid bmx file"

        return false
    }

    sub build_header() {
        ; build the internal BMX header structure
        ; normally you don't have to call this yourself
        sys.memset(header, sizeof(header), 0)
        uword data_offset = palette_entries*$0002
        data_offset += sizeof(header)
        header[0] = FILEID[0]
        header[1] = FILEID[1]
        header[2] = FILEID[2]
        header[3] = 1        ; version 1
        header[4] = bitsperpixel
        header[5] = vera_colordepth
        header[6] = lsb(width)
        header[7] = msb(width)
        header[8] = lsb(height)
        header[9] = msb(height)
        header[10] = lsb(palette_entries)
        header[11] = palette_start
        header[12] = lsb(data_offset)
        header[13] = msb(data_offset)
        header[14] = compression
        header[15] = border
    }
}
