%target cx16
%import gfx2
%import diskio

bmp_module {

    sub show_image(uword filenameptr) -> ubyte {
        ubyte load_ok = false
        ubyte[$36] header
        uword size
        uword width
        uword height
        ubyte bpp
        uword offsetx
        uword offsety
        uword palette = memory("palette", 256*4)
        uword total_read = 0

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
                    size = diskio.f_read(palette, num_colors*4)
                    if size==num_colors*4 {
                        total_read += size
                        repeat bm_data_offset - total_read
                            void c64.CHRIN()
                        gfx2.clear_screen()
                        custompalette.set_bgra(palette, num_colors)
                        decode_bitmap()
                        load_ok = true
                    }
                }
            }

            diskio.f_close()
        }

        return load_ok

        sub start_plot() {
            offsetx = 0
            offsety = 0
            if width < gfx2.width
                offsetx = (gfx2.width - width - 1) / 2
            if height < gfx2.height
                offsety = (gfx2.height - height - 1) / 2
            if width > gfx2.width
                width = gfx2.width
            if height > gfx2.height
                height = gfx2.height
        }

        sub decode_bitmap() {
            start_plot()
            uword bits_width = width * bpp
            ubyte pad_bytes = (((bits_width + 31) >> 5) << 2) - ((bits_width + 7) >> 3) as ubyte

            uword x
            uword y
            ubyte b
            for y in height-1 downto 0 {
                gfx2.position(offsetx, offsety+y)
                when bpp {
                    8 -> {
                        for x in 0 to width-1
                            gfx2.next_pixel(c64.CHRIN())
                    }
                    4 -> {
                        for x in 0 to width-1 step 2 {
                            b = c64.CHRIN()
                            gfx2.next_pixel(b>>4)
                            gfx2.next_pixel(b&15)
                        }
                    }
                    2 -> {
                        for x in 0 to width-1 step 4 {
                            b = c64.CHRIN()
                            gfx2.next_pixel(b>>6)
                            gfx2.next_pixel(b>>4 & 3)
                            gfx2.next_pixel(b>>2 & 3)
                            gfx2.next_pixel(b & 3)
                        }
                    }
                    1 -> {
                        for x in 0 to width-1 step 8
                            gfx2.set_8_pixels_from_bits(c64.CHRIN(), 1, 0)
                    }
                }

                repeat pad_bytes
                    void c64.CHRIN()
            }
        }
    }
}
