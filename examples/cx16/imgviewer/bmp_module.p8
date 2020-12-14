%target cx16
%import graphics
%import textio
%import diskio

bmp_module {

    sub show_image(uword filenameptr) {
        ubyte load_ok = false
        txt.print(filenameptr)
        txt.chrout('\n')

        ubyte[$36] header
        uword size
        uword width
        uword height
        ubyte bpp
        uword offsetx
        uword offsety
        ubyte[256] palette0
        ubyte[256] palette1
        ubyte[256] palette2
        ubyte[256] palette3
        uword total_read = 0

        palette0[0] = 0
        palette1[0] = 0
        palette2[0] = 0
        palette3[0] = 0

        if diskio.f_open(8, filenameptr) {
            size = diskio.f_read(header, $36)
            if size==$36 {
                total_read = $36
                if header[0]=='b' and header[1]=='m' {
                    uword bm_data_offset = mkword(header[11], header[10])
                    uword header_size = mkword(header[$f], header[$e])
                    width = mkword(header[$13], header[$12])
                    height = mkword(header[$17], header[$16])
                    bpp = header[$1c]
                    uword num_colors = header[$2e]
                    if num_colors == 0
                        num_colors = 2**bpp
                    uword skip_hdr = header_size - 40
                    repeat skip_hdr
                        void c64.CHRIN()
                    total_read += skip_hdr
                    size = diskio.f_read(&palette0, num_colors*4)
                    if size==num_colors*4 {
                        total_read += size
                        repeat bm_data_offset - total_read
                            void c64.CHRIN()
                        graphics.clear_screen(1, 0)
                        set_palette(&palette0, num_colors)
                        decode_bitmap()
                        load_ok = true
                    }
                } else
                    txt.print("not a bmp file!\n")
            }

            diskio.f_close()
        }

        if not load_ok {
            txt.print("load error!\n")
        }

        sub start_plot() {
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

        sub decode_bitmap() {
            start_plot()
            uword bits_width = width * bpp
            ubyte pad_bytes = (((bits_width + 31) >> 5) << 2) - ((bits_width + 7) >> 3) as ubyte

            uword x
            uword y
            ubyte b
            for y in height-1 downto 0 {
                set_cursor(0, y)
                when bpp {
                    8 -> {
                        for x in 0 to width-1
                            cx16.FB_set_pixel(c64.CHRIN())
                    }
                    4 -> {
                        for x in 0 to width-1 step 2 {
                            b = c64.CHRIN()
                            cx16.FB_set_pixel(b>>4)
                            cx16.FB_set_pixel(b&15)
                        }
                    }
                    2 -> {
                        for x in 0 to width-1 step 4 {
                            b = c64.CHRIN()
                            cx16.FB_set_pixel(b>>6)
                            cx16.FB_set_pixel(b>>4 & 3)
                            cx16.FB_set_pixel(b>>2 & 3)
                            cx16.FB_set_pixel(b & 3)
                        }
                    }
                    1 -> {
                        for x in 0 to width-1 step 8 {
                            cx16.r0 = c64.CHRIN()
                            cx16.FB_set_8_pixels_opaque(255, 255, 0)
                        }
                    }
                }

                repeat pad_bytes
                    void c64.CHRIN()
            }
        }
    }

    sub set_palette(uword palletteptr, uword num_colors) {
        uword vera_palette_ptr = $fa00
        ubyte red
        ubyte greenblue

        ; 4 bytes per color entry (BGRA), adjust color depth from 8 to 4 bits per channel.
        repeat num_colors {
            red = @(palletteptr+2) >> 4
            greenblue = @(palletteptr+1) & %11110000
            greenblue |= @(palletteptr+0) >> 4    ; add Blue
            palletteptr+=4
            cx16.vpoke(1, vera_palette_ptr, greenblue)
            vera_palette_ptr++
            cx16.vpoke(1, vera_palette_ptr, red)
            vera_palette_ptr++
        }
    }

}
