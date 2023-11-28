; Routines to load and save "BMX" files (commander X16 bitmap format) Version 1.
; Only uncompressed images, of width 320 or 640, are supported for now.
; BMX Specification: https://cx16forum.com/forum/viewtopic.php?t=6945

%import diskio

bmx {

    ubyte[32] header
    str FILEID = petscii:"bmx"

    ubyte bitsperpixel          ; consider using set_bpp() when setting this
    ubyte vera_colordepth       ; consider using set_vera_colordepth() when setting this
    uword width
    uword height
    ubyte border
    ubyte palette_entries       ; 0 means 256, all of them
    ubyte palette_start
    ubyte compression

    uword error_message             ; pointer to error message, or 0 if all ok
    uword max_width = 0             ; should you want load() to check for this
    uword max_height = 0            ; should you want load() to check for this
    uword palette_buffer_ptr = 0    ; should you want to load or save the palette into main memory instead of directly into vram

    sub load(ubyte drivenumber, str filename, ubyte vbank, uword vaddr) -> bool {
        ; Loads a BMX bitmap image and palette into vram. (and Header info into the bmx.* variables)
        ; Parameters:
        ; the drive number and filename to load,
        ; and the vram bank and address where the bitmap data should go,
        ; You can set the max_width and max_height variables first, if you want this routine to check those.
        ; Note: does not change vera screen mode or colordepth! You have to do that yourself!
        ; Returns: success status. If false, error_message points to the error message string.
        error_message = 0
        ubyte old_drivenumber = diskio.drivenumber
        diskio.drivenumber = drivenumber
        if diskio.f_open(filename) {
            diskio.reset_read_channel()
            if read_header() {
                if parse_header() {
                    if max_width and width>max_width {
                        error_message = "image too large"
                        goto load_end
                    }
                    if max_height and height>max_height {
                        error_message = "image too large"
                        goto load_end
                    }
                    if width!=320 and width!=640 {
                        error_message = "width not 320 or 640"      ; TODO: deal with other widths
                        goto load_end
                    }
                    if compression {
                        error_message = "compression not supported"
                        goto load_end
                    }
                    if read_palette() {
                        if not read_bitmap(vbank, vaddr)
                            error_message = "bitmap error"
                    } else
                        error_message = "palette error"
                } else
                    error_message = "invalid bmx file"
            } else
                error_message = "invalid bmx file"
        } else
            error_message = diskio.status()

load_end:
        diskio.f_close()
        diskio.drivenumber = old_drivenumber
        return error_message==0
    }

    sub load_header(ubyte drivenumber, str filename) -> bool {
        ; Loads just the header data from a BMX bitmap image into the bmx.* variables.
        ; Parameters: the drive number and filename to load.
        ; Returns: success status. If false, error_message points to the error message string.
        error_message = 0
        ubyte old_drivenumber = diskio.drivenumber
        diskio.drivenumber = drivenumber
        if diskio.f_open(filename) {
            diskio.reset_read_channel()
            if read_header() {
                if not parse_header()
                    error_message = "invalid bmx file"
            } else
                error_message = "invalid bmx file"
        } else
            error_message = diskio.status()

load_end:
        diskio.f_close()
        diskio.drivenumber = old_drivenumber
        return error_message==0
    }

    sub load_palette(ubyte drivenumber, str filename) -> bool {
        ; Loads just the palette from a BMX bitmap image into vram or the palette buffer.
        ; (and Header info into the bmx.* variables).
        ; Parameters: the drive number and filename to load.
        ; Returns: success status. If false, error_message points to the error message string.
        error_message = 0
        ubyte old_drivenumber = diskio.drivenumber
        diskio.drivenumber = drivenumber
        if diskio.f_open(filename) {
            diskio.reset_read_channel()
            if read_header() {
                if parse_header() {
                    if not read_palette()
                        error_message = "palette error"
                } else
                    error_message = "invalid bmx file"
            } else
                error_message = "invalid bmx file"
        } else
            error_message = diskio.status()

load_end:
        diskio.f_close()
        diskio.drivenumber = old_drivenumber
        return error_message==0
    }

    sub save(ubyte drivenumber, str filename, ubyte vbank, uword vaddr) -> bool {
        ; Save bitmap and palette data from vram into a BMX file.
        ; First you must have set all bmx.* variables to the correct values! (like width, height..)
        ; Parameters:
        ; drive number and filename to save to,
        ; vram bank and address of the bitmap data to save.
        ; Returns: success status. If false, error_message points to the error message string.
        ; TODO: how to save bitmaps that are not the full visible screen width (non-contiguous scanlines)
        if compression {
            error_message = "compression not supported"
            return false
        }
        error_message = 0
        if width!=320 and width!=640 {
            error_message = "width not 320 or 640"      ; TODO: deal with other widths
            goto save_end
        }
        ubyte old_drivenumber = diskio.drivenumber
        diskio.drivenumber = drivenumber
        if diskio.f_open_w(filename) {
            cx16.r0 = diskio.status()
            if cx16.r0[0]!='0' {
                error_message = cx16.r0
                goto save_end
            }
            diskio.reset_write_channel()
            if write_header() {
                if write_palette() {
                    if not write_bitmap(vbank, vaddr)
                        error_message = "bitmap error"
                } else
                    error_message = "palette error"
            } else
                error_message = "header error"
        } else
             error_message = diskio.status()
save_end:
        diskio.f_close_w()
        diskio.drivenumber = old_drivenumber
        return error_message==0
    }

    sub set_bpp(ubyte bpp) {
        bitsperpixel = bpp
        vera_colordepth = 0
        when bpp {
            2 -> vera_colordepth = 1
            4 -> vera_colordepth = 2
            8 -> vera_colordepth = 3
        }
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
        return not cbm.READST()
    }

    sub read_palette() -> bool {
        ; load palette data from the currently active input file
        ; if palette_buffer_ptr is not 0, the palette data is read into that memory buffer,
        ; otherwise it is read directly into the palette in vram.
        cx16.vaddr(1, $fa00+palette_start*2, 0, 1)
        cx16.r3 = palette_buffer_ptr
        cx16.r2L = palette_entries
        do {
            cx16.r4L = cbm.CHRIN()
            cx16.r4H = cbm.CHRIN()
            if cx16.r3 {
                pokew(cx16.r3, cx16.r4)             ; into memory
                cx16.r3+=2
            } else {
                cx16.VERA_DATA0 = cx16.r4L          ; into vram
                cx16.VERA_DATA0 = cx16.r4H
            }
            cx16.r2L--
        } until cx16.r2L==0
        return cbm.READST()==0 or cbm.READST()&$40    ; no error or eof?
    }

    sub read_bitmap(ubyte vbank, uword vaddr) -> bool {
        ; load contiguous bitmap into vram from the currently active input file
        ; TODO how to deal with bitmaps that are smaller than the screen?
        cx16.vaddr(vbank, vaddr, 0, 1)
        cx16.r3 = bytes_per_scanline(width)
        repeat height
            read_scanline(cx16.r3)
        return cbm.READST()==0 or cbm.READST()&$40    ; no error or eof?

        sub read_scanline(uword size) {
            while size {
                cx16.r0 = cx16.MACPTR(min(255, size) as ubyte, &cx16.VERA_DATA0, true)
                if_cs {
                    ; no MACPTR support
                    repeat size
                        cx16.VERA_DATA0 = cbm.CHRIN()
                    return
                }
                size -= cx16.r0
            }
            return
        }
    }

    sub write_header() -> bool {
        ; save out the BMX header to the currently active output file
        build_header()
        for cx16.r0L in 0 to sizeof(header)-1 {
            cbm.CHROUT(header[cx16.r0L])
        }
        return not cbm.READST()
    }

    sub write_palette() -> bool {
        ; write palette data to the currently active input file
        ; if palette_buffer_ptr is not 0, the palette data is read from that memory buffer,
        ; otherwise it is read directly from the palette in vram.
        cx16.r3 = palette_buffer_ptr
        cx16.r2L = palette_entries
        cx16.vaddr(1, $fa00+palette_start*2, 0, 1)
        do {
            if cx16.r3 {
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
        return not cbm.READST()
    }

    sub write_bitmap(ubyte vbank, uword vaddr) -> bool {
        ; save contiguous bitmap from vram to the currently active output file
        ; TODO how to deal with bitmaps that are smaller than the screen
        cx16.vaddr(vbank, vaddr, 0, 1)
        cx16.r3 = bytes_per_scanline(width)
        repeat height
            write_scanline(cx16.r3)
        return not cbm.READST()

        sub write_scanline(uword size) {
            while size {
                cx16.r0L = lsb(size)
                if msb(size)
                    cx16.r0L = 0        ; 256 bytes
                cx16.r0 = cx16.MCIOUT(cx16.r0L, &cx16.VERA_DATA0, true)
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
        when bitsperpixel {
            1 -> cx16.r0L = 3
            2 -> cx16.r0L = 2
            4 -> cx16.r0L = 1
            8 -> return w
            else -> return 0
        }
        return w >> cx16.r0L
    }

    sub parse_header() -> bool {
        if header[0]==FILEID[0] and header[1]==FILEID[1] and header[2]==FILEID[2] {
            if header[3]==1 {       ; only version 1 supported for now
                bitsperpixel = header[4]
                vera_colordepth = header[5]
                width = peekw(&header+6)
                height = peekw(&header+8)
                palette_entries = header[10]
                palette_start = header[11]
                ; the data offset is not needed:  data_offset = peekw(&header+12)
                compression = header[14]
                border = header[15]
                return true
            }
        }
        return false
    }

    sub build_header() {
        ; build the internal BMX header structure
        ; normally you don't have to call this yourself
        sys.memset(header, sizeof(header), 0)
        uword data_offset = 512     ; full palette of 256 entries
        if palette_entries
            data_offset = palette_entries*$0002
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
        header[10] = palette_entries
        header[11] = palette_start
        header[12] = lsb(data_offset)
        header[13] = msb(data_offset)
        header[14] = compression
        header[15] = border
    }
}
