%target cx16
%import gfx2
%import diskio
%import palette

koala_module {
    const uword load_location = $6000

    sub show_image(uword filenameptr) -> ubyte {
        ubyte load_ok=false
        if diskio.f_open(8, filenameptr) {
            uword size = diskio.f_read(load_location, 2)    ; skip the first 2 bytes (load address)
            if size==2 {
                if diskio.f_read(load_location, 10001)==10001 {
                    ; set a better C64 color palette, the Cx16's default is too saturated
                    palette.set_c64pepto()
                    convert_koalapic()
                    load_ok = true
                }
            }
            diskio.f_close()
        }

        return load_ok
    }

    sub convert_koalapic() {
        ubyte cy
        ubyte @zp cx
        uword @zp cy_times_forty = 0
        ubyte @zp d
        uword bitmap_ptr = load_location

        ; theoretically you could put the 8-pixel array in zeropage to squeeze out another tiny bit of performance
        ubyte[8] pixels

        gfx2.clear_screen()
        uword offsety = (gfx2.height - 200) / 2

        for cy in 0 to 24*8 step 8 {
            uword posy = cy + offsety
            for cx in 0 to 39 {
                uword posx = cx as uword * 8
                for d in 0 to 7 {
                    gfx2.position(posx, posy + d)
                    get_8_pixels()
                    gfx2.next_pixels(pixels, 8)
                }
            }
            cy_times_forty += 40
        }

        sub get_8_pixels() {
            ubyte  bm = @(bitmap_ptr)
            ubyte  @zp  m = mcol(bm)
            pixels[7] = m
            pixels[6] = m
            bm >>= 2
            m = mcol(bm)
            pixels[5] = m
            pixels[4] = m
            bm >>= 2
            m = mcol(bm)
            pixels[3] = m
            pixels[2] = m
            bm >>= 2
            m = mcol(bm)
            pixels[1] = m
            pixels[0] = m
            bitmap_ptr++

            sub mcol(ubyte b) -> ubyte {
                ubyte @zp color
                when b & 3 {
                    0 -> color = @(load_location + 8000 + 1000 + 1000) & 15
                    1 -> color = @(load_location + 8000 + cy_times_forty + cx) >>4
                    2 -> color = @(load_location + 8000 + cy_times_forty + cx) & 15
                    else -> color = @(load_location + 8000 + 1000 + cy_times_forty + cx) & 15
                }
                return color
            }
        }
    }
}
