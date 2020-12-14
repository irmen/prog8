%target cx16
%import graphics
%import textio
%import diskio

main {
    sub start() {
        graphics.enable_bitmap_mode()

        if strlen(diskio.status(8))     ; trick to check if we're running on sdcard or host system shared folder
            show_pics_sdcard()
        else {
            txt.print("only works with files on\nsdcard image!\n")
        }

        repeat {
            ;
        }
    }

    sub show_pics_sdcard() {

        ; load and show all *.iff pictures on the disk.
        ; this only works in the emulator V38 with an sd-card image with the files on it.

        str[20] filename_ptrs
        ubyte num_files = diskio.list_files(8, ".iff", true, &filename_ptrs, len(filename_ptrs))
        if num_files {
            while num_files {
                num_files--
                iff.show_iff_image(filename_ptrs[num_files])
                cx16.wait(120)
            }
        } else {
            txt.print("no *.iff files found\n")
        }
    }

    sub set_palette_8rgb(uword palletteptr, uword num_colors) {
        uword vera_palette_ptr = $fa00
        ubyte red
        ubyte greenblue

        ; 3 bytes per color entry, adjust color depth from 8 to 4 bits per channel.
        repeat num_colors {
            red = @(palletteptr) >> 4
            palletteptr++
            greenblue = @(palletteptr) & %11110000
            palletteptr++
            greenblue |= @(palletteptr) >> 4    ; add Blue
            palletteptr++
            cx16.vpoke(1, vera_palette_ptr, greenblue)
            vera_palette_ptr++
            cx16.vpoke(1, vera_palette_ptr, red)
            vera_palette_ptr++
        }
    }

}

iff {
    sub show_iff_image(uword filenameptr) {
        ubyte load_ok = false
        txt.print(filenameptr)
        txt.chrout('\n')

        uword size
        ubyte[32] buffer
        ubyte[256] cmap
        ubyte[256] cmap1
        ubyte[256] cmap2
        uword camg = 0
        str chunk_id = "????"
        uword chunk_size_hi
        uword chunk_size_lo
        uword scanline_data_ptr = progend()

        uword width
        uword height
        ubyte num_planes
        uword num_colors
        ubyte compression
        ubyte have_cmap = false
        cmap[0] = 0
        cmap1[0] = 0
        cmap2[0] = 0

        if diskio.f_open(8, filenameptr) {
            size = diskio.f_read(buffer, 12)
            if size==12 {
                if buffer[0]=='f' and buffer[1]=='o' and buffer[2]=='r' and buffer[3]=='m'
                        and buffer[8]=='i' and buffer[9]=='l' and buffer[10]=='b' and buffer[11]=='m'{

                    while read_chunk_header() {
                        if chunk_id == "bmhd" {
                            void diskio.f_read(buffer, chunk_size_lo)
                            width = mkword(buffer[0], buffer[1])
                            height = mkword(buffer[2], buffer[3])
                            num_planes = buffer[8]
                            num_colors = 2 ** num_planes
                            compression = buffer[10]
                        }
                        else if chunk_id == "camg" {
                            void diskio.f_read(buffer, chunk_size_lo)
                            camg = mkword(buffer[2], buffer[3])
                            if camg & $0800 {
                                txt.print("ham mode not supported!\n")
                                break
                            }
                        }
                        else if chunk_id == "cmap" {
                            have_cmap = true
                            void diskio.f_read(&cmap, chunk_size_lo)
                        }
                        else if chunk_id == "body" {
                            txt.clear_screen()
                            graphics.clear_screen(1, 0)
                            if camg & $0004
                                height /= 2     ; interlaced: just skip every odd scanline later
                            if camg & $0080 and have_cmap
                                make_ehb_palette()
                            main.set_palette_8rgb(&cmap, num_colors)
                            if compression
                                decode_rle()
                            else
                                decode_raw()
                            load_ok = true
                            break   ; done after body
                        }
                        else {
                            skip_chunk()
                        }
                    }
                } else
                    txt.print("not an iff ilbm file!\n")
            }

            diskio.f_close()
        }

        if not load_ok {
            txt.print("load error!\n")
            txt.print(diskio.status(8))
        }


        sub read_chunk_header() -> ubyte {
            size = diskio.f_read(buffer, 8)
            if size==8 {
                chunk_id[0] = buffer[0]
                chunk_id[1] = buffer[1]
                chunk_id[2] = buffer[2]
                chunk_id[3] = buffer[3]
                chunk_size_hi = mkword(buffer[4], buffer[5])
                chunk_size_lo = mkword(buffer[6], buffer[7])
                return true
            }
            return false
        }

        sub skip_chunk() {
            repeat lsb(chunk_size_hi)*8 + (chunk_size_lo >> 13)
                void diskio.f_read(scanline_data_ptr, $2000)

            void diskio.f_read(scanline_data_ptr, chunk_size_lo & $1fff)
        }

        sub make_ehb_palette() {
            ; generate 32 additional Extra-Halfbrite colors in the cmap
            uword palletteptr = &cmap
            uword ehbptr = palletteptr + 32*3
            repeat 32 {
                @(ehbptr) = @(palletteptr)>>1
                ehbptr++
                palletteptr++
                @(ehbptr) = @(palletteptr)>>1
                ehbptr++
                palletteptr++
                @(ehbptr) = @(palletteptr)>>1
                ehbptr++
                palletteptr++
            }
        }

        uword bitplane_stride
        uword interleave_stride
        uword offsetx
        uword offsety

        sub start_plot() {
            bitplane_stride = width>>3
            interleave_stride = bitplane_stride * num_planes
            offsetx = 0
            offsety = 0
            if width < graphics.WIDTH
                offsetx = (graphics.WIDTH - width - 1) / 2
            if height < graphics.HEIGHT
                offsety = (graphics.HEIGHT - height - 1) / 2
            if width > graphics.WIDTH
                width = graphics.WIDTH
            if height > graphics.HEIGHT-1
                height = graphics.HEIGHT-1
        }

        sub set_cursor(uword x, uword y) {
            cx16.r0=offsetx+x
            cx16.r1=offsety+y
            cx16.FB_cursor_position()
        }

        sub decode_raw() {
            start_plot()
            ubyte interlaced = (camg & $0004) != 0
            uword y
            for y in 0 to height-1 {
                void diskio.f_read(scanline_data_ptr, interleave_stride)
                if interlaced
                    void diskio.f_read(scanline_data_ptr, interleave_stride)
                set_cursor(0, y)
                planar_to_chunky_scanline()
            }
        }

        sub decode_rle() {
            start_plot()
            ubyte interlaced = (camg & $0004) != 0
            uword y
            for y in 0 to height-1 {
                decode_rle_scanline()
                if interlaced
                    decode_rle_scanline()
                set_cursor(0, y)
                planar_to_chunky_scanline()
            }
        }

        sub decode_rle_scanline() {
            uword x = interleave_stride
            uword plane_ptr = scanline_data_ptr

            while x {
                ubyte b = c64.CHRIN()
                if b > 128 {
                    ubyte b2 = c64.CHRIN()
                    repeat 2+(b^255) {
                        @(plane_ptr) = b2
                        plane_ptr++
                        x--
                    }
                } else if b < 128 {
                    repeat b+1 {
                        @(plane_ptr) = c64.CHRIN()
                        plane_ptr++
                        x--
                    }
                } else
                    break
            }
        }

        sub planar_to_chunky_scanline() {
            uword x
            for x in 0 to width-1 {
                ubyte bitnr = ((lsb(x) ^ 255) & 7) + 1
                uword pixptr = x/8 + scanline_data_ptr
                ubyte bits=0
                repeat num_planes {
                    ubyte bb = @(pixptr) >> bitnr       ; extract bit
                    ror(bits)           ; shift it into the byte
                    pixptr += bitplane_stride
                }
                bits >>= 8-num_planes
                cx16.FB_set_pixel(bits)
            }
        }
    }
}
