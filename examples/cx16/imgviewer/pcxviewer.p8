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

        ; load and show all *.pcx pictures on the disk.
        ; this only works in the emulator V38 with an sd-card image with the files on it.

        str[20] filename_ptrs
        ubyte num_files = diskio.list_files(8, ".pcx", true, &filename_ptrs, len(filename_ptrs))
        if num_files {
            while num_files {
                num_files--
                show_pcx_image(filename_ptrs[num_files])
                cx16.wait(120)
            }
        } else {
            txt.print("no *.pcx files found\n")
        }
    }

    sub show_pcx_image(uword filenameptr) {
        ubyte load_ok = false
        txt.print(filenameptr)
        txt.chrout('\n')

        if diskio.f_open(8, filenameptr) {
            ubyte[128] header
            uword size = diskio.f_read(header, 128)
            if size==128 {
                if header[0] == $0a and header[2] == 1 {
                    ubyte bits_per_pixel = header[3]
                    if bits_per_pixel==1 or bits_per_pixel==4 or bits_per_pixel==8 {
                        uword width = mkword(header[$09], header[$08]) - mkword(header[$05], header[$04]) + 1
                        uword height = mkword(header[$0b], header[$0a]) - mkword(header[$07], header[$06]) + 1
                        ubyte number_of_planes = header[$41]
                        uword palette_format = mkword(header[$45], header[$44])
                        uword num_colors = 2**bits_per_pixel
                        if number_of_planes == 1 {
                            if (width & 7) == 0 {
                                txt.print_uw(width)
                                txt.chrout('x')
                                txt.print_uw(height)
                                txt.print(", ")
                                txt.print_uw(num_colors)
                                txt.print(" colors\n")
                                if palette_format==2
                                    set_grayscale_palette()
                                else if num_colors == 16
                                    set_palette(&header + $10, 16)
                                else if num_colors == 2
                                    set_monochrome_palette()
                                when bits_per_pixel {
                                    8 -> load_ok = bitmap.do8bpp(width, height)
                                    4 -> load_ok = bitmap.do4bpp(width, height)
                                    1 -> load_ok = bitmap.do1bpp(width, height)
                                }
                                if load_ok {
                                    txt.clear_screen()
                                    graphics.clear_screen(1, 0)
                                    load_ok = c64.CHRIN()
                                    if load_ok == 12 {
                                        ; there is 256 colors of palette data at the end
                                        uword palette_mem = progend()
                                        load_ok = false
                                        size = diskio.f_read(palette_mem, 3*256)
                                        if size==3*256 {
                                            load_ok = true
                                            set_palette(palette_mem, num_colors)
                                        }
                                    }
                                }
                            } else
                                txt.print("width not multiple of 8!\n")
                        } else
                            txt.print("has >256 colors!\n")
                    }
                }
            }
            diskio.f_close()
        }

        if not load_ok {
            txt.print("load error!\n")
            txt.print(diskio.status(8))
        }
    }

    sub set_monochrome_palette() {
        uword vera_palette_ptr = $fa00
        cx16.vpoke(1, vera_palette_ptr, 0)
        vera_palette_ptr++
        cx16.vpoke(1, vera_palette_ptr, 0)
        vera_palette_ptr++
        repeat 255 {
            cx16.vpoke(1, vera_palette_ptr, 255)
            vera_palette_ptr++
            cx16.vpoke(1, vera_palette_ptr, 255)
            vera_palette_ptr++
        }
    }

    sub set_grayscale_palette() {
        ubyte c = 0
        uword vera_palette_ptr = $fa00
        repeat 16 {
            repeat 16 {
                cx16.vpoke(1, vera_palette_ptr, c)
                vera_palette_ptr++
                cx16.vpoke(1, vera_palette_ptr, c)
                vera_palette_ptr++
            }
            c += $11
        }
    }

    sub set_palette(uword palletteptr, uword num_colors) {
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

bitmap {

    uword offsetx
    uword offsety
    uword py
    uword px
    ubyte y_ok
    ubyte status

    sub start_plot(uword width, uword height) {
        offsetx = 0
        offsety = 0
        y_ok = true
        py = 0
        px = 0
        if width < graphics.WIDTH
            offsetx = (graphics.WIDTH - width - 1) / 2
        if height < graphics.HEIGHT
            offsety = (graphics.HEIGHT - height - 1) / 2
        status = (not c64.READST()) or c64.READST()==64
    }

    sub set_cursor(uword x, uword y) {
        cx16.r0=offsetx+x
        cx16.r1=offsety+y
        cx16.FB_cursor_position()
    }

    sub next_scanline() {
        px = 0
        py++
        y_ok = py < graphics.HEIGHT-1
        set_cursor(0, py)
        status = (not c64.READST()) or c64.READST()==64
    }

    sub do1bpp(uword width, uword height) -> ubyte {
        start_plot(width, height)
        set_cursor(0, 0)
        while py < height and status {
            ubyte b = c64.CHRIN()
            if b>>6==3 {
                b &= %00111111
                ubyte dat = c64.CHRIN()
                repeat b {
                    if y_ok {
                        cx16.r0 = dat
                        cx16.FB_set_8_pixels_opaque(255, 255, 0)
                    }
                    px += 8
                }
            } else {
                if y_ok {
                    cx16.r0 = b
                    cx16.FB_set_8_pixels_opaque(255, 255, 0)
                }
                px += 8
            }
            if px==width
                next_scanline()
        }

        return status
    }

    sub do4bpp(uword width, uword height) -> ubyte {
        start_plot(width, height)
        set_cursor(0, 0)
        while py < height and status {
            ubyte b = c64.CHRIN()
            if b>>6==3 {
                b &= %00111111
                ubyte dat = c64.CHRIN()
                if y_ok
                    repeat b {
                        cx16.FB_set_pixel(dat>>4)
                        cx16.FB_set_pixel(dat & 15)
                    }
                px += b*2
            } else {
                if y_ok {
                    cx16.FB_set_pixel(b>>4)
                    cx16.FB_set_pixel(b & 15)
                }
                px += 2
            }
            if px==width
                next_scanline()
        }

        return status
    }

    sub do8bpp(uword width, uword height) -> ubyte {
        start_plot(width, height)
        set_cursor(0, 0)
        while py < height and status {
            ubyte b = c64.CHRIN()
            if b>>6==3 {
                b &= %00111111
                ubyte dat = c64.CHRIN()
                if y_ok
                    repeat b
                        cx16.FB_set_pixel(dat)
                px += b
            } else {
                if y_ok
                    cx16.FB_set_pixel(b)
                px++
            }
            if px==width
                next_scanline()
        }

        return status
    }
}
